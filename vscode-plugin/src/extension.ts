import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

let fileWatcher: vscode.FileSystemWatcher | null = null;

export function activate(context: vscode.ExtensionContext) {
    const config = vscode.workspace.getConfiguration('featureFlagHints');
    
    if (!config.get('enable')) {
        return;
    }

    const filePath: string = config.get('filePath') ?? 'feature-flags.json';
    const provider = new FeatureFlagInlayHintsProvider(filePath);

    context.subscriptions.push(
        vscode.languages.registerInlayHintsProvider(
            { language: 'go' },
            provider
        )
    );

    context.subscriptions.push(
        vscode.workspace.onDidChangeConfiguration((e) => {
            if (e.affectsConfiguration('featureFlagHints')) {
                provider.refresh();
            }
        })
    );

    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (workspaceFolders && workspaceFolders.length > 0) {
        const pattern = new vscode.RelativePattern(workspaceFolders[0], filePath);
        fileWatcher = vscode.workspace.createFileSystemWatcher(pattern);
        
        context.subscriptions.push(
            fileWatcher,
            fileWatcher.onDidChange(() => provider.refresh()),
            fileWatcher.onDidCreate(() => provider.refresh()),
            fileWatcher.onDidDelete(() => provider.refresh())
        );
    }
}

export function deactivate() {}

class FeatureFlagInlayHintsProvider implements vscode.InlayHintsProvider {
    private _onDidChangeInlayHints = new vscode.EventEmitter<void>();
    private flags: Map<string, boolean> | null = null;
    private workspaceRoot: string | undefined;
    private resolveTimer: NodeJS.Timeout | null = null;

    onDidChangeInlayHints = this._onDidChangeInlayHints.event;

    constructor(private filePath: string) {
        this.workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
        this.loadFlags();
    }

    refresh(): void {
        this.loadFlags();
        this._onDidChangeInlayHints.fire();
    }

    private loadFlags(): void {
        if (!this.workspaceRoot) {
            this.flags = null;
            return;
        }

        const fullPath = path.join(this.workspaceRoot, this.filePath);
        
        try {
            if (!fs.existsSync(fullPath)) {
                this.flags = null;
                return;
            }

            const content = fs.readFileSync(fullPath, 'utf-8');
            this.flags = this.parseFlags(content);
        } catch (error) {
            console.error('Error loading feature flags:', error);
            this.flags = null;
        }
    }

    private parseFlags(json: string): Map<string, boolean> {
        const result = new Map<string, boolean>();
        const pattern = /"([^"]+)"\s*:\s*(true|false)/g;
        let match;

        while ((match = pattern.exec(json)) !== null) {
            result.set(match[1], match[2] === 'true');
        }

        return result;
    }

    provideInlayHints(
        document: vscode.TextDocument,
        range: vscode.Range,
        token: vscode.CancellationToken
    ): vscode.InlayHint[] | null {
        if (!this.flags || this.flags.size === 0) {
            return null;
        }

        const hints: vscode.InlayHint[] = [];
        const text = document.getText();
        const handledRanges = new Set<string>();

        const isenabledPattern = /(\w+\.?)?IsEnabled\s*\(\s*[^,]+,\s*(["']([^"']+)["']|[a-zA-Z_]\w*)\s*\)/g;

        let match;
        while ((match = isenabledPattern.exec(text)) !== null) {
            if (token.isCancellationRequested) {
                return null;
            }

            const matchText = match[0];
            const matchKey = `${match.index}-${match.index + matchText.length}`;
            if (handledRanges.has(matchKey)) {
                continue;
            }
            handledRanges.add(matchKey);

            const flagValue = match[3] ?? match[4];
            if (!flagValue) continue;

            let flagKey: string | null = flagValue;
            
            if (!/^[a-zA-Z_]\w*$/.test(flagKey)) {
                flagKey = null;
            }

            if (!flagKey) {
                continue;
            }

            const resolvedKey = this.resolveConstant(document, flagKey) ?? flagKey;
            const enabled = this.flags.get(resolvedKey);

            if (enabled !== undefined) {
                const argMatch = matchText.match(/(["']([^"']+)["']|[a-zA-Z_]\w*)\s*\)$/);
                if (argMatch) {
                    const argPos = match.index + matchText.length - argMatch[0].length + argMatch[1].length;
                    const position = document.positionAt(argPos);

                    const hint = new vscode.InlayHint(
                        position,
                        ` ${enabled ? 'true' : 'false'}`,
                        vscode.InlayHintKind.Parameter
                    );

                    hints.push(hint);
                }
            }
        }

        return hints;
    }

    private resolveConstant(document: vscode.TextDocument, name: string): string | null {
        const text = document.getText();
        const constPattern = new RegExp(`const\\s+${name}\\s*=\\s*"([^"]+)"`);
        const match = text.match(constPattern);
        if (match) {
            return match[1];
        }

        const groupPattern = /const\s*\(([\s\S]*?)\)/g;
        let match2;
        while ((match2 = groupPattern.exec(text)) !== null) {
            const groupContent = match2[1];
            const linePattern = new RegExp(`^\\s*${name}\\s*=\\s*"([^"]+)"`, 'm');
            const lineMatch = groupContent.match(linePattern);
            if (lineMatch) {
                return lineMatch[1];
            }
        }

        return null;
    }

    resolveInlayHint?(hint: vscode.InlayHint, token: vscode.CancellationToken): vscode.InlayHint {
        return hint;
    }
}

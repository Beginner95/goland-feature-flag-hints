package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"sort"
	"time"
)

type fliptFlagsResponse struct {
	Flags         []fliptFlag `json:"flags"`
	NextPageToken string      `json:"nextPageToken"`
}

type fliptFlag struct {
	Key     string `json:"key"`
	Enabled bool   `json:"enabled"`
}

func main() {
	fliptURL := os.Getenv("FLIPT_URL")
	if fliptURL == "" {
		fmt.Fprintln(os.Stderr, "error: FLIPT_URL env var is required")
		fmt.Fprintln(os.Stderr, "  example: FLIPT_URL=https://flipt.example.com flipt-sync")
		os.Exit(1)
	}

	namespace := os.Getenv("FLIPT_NAMESPACE")
	if namespace == "" {
		namespace = "default"
	}

	outputPath := os.Getenv("OUTPUT_PATH")
	if outputPath == "" {
		outputPath = "feature-flags.json"
	}

	token := os.Getenv("FLIPT_TOKEN")

	client := &http.Client{Timeout: 15 * time.Second}

	flags, err := fetchAllFlags(client, fliptURL, namespace, token)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error fetching flags: %v\n", err)
		os.Exit(1)
	}

	result := make(map[string]bool, len(flags))
	for _, f := range flags {
		result[f.Key] = f.Enabled
	}

	data, err := marshalSorted(result)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error marshalling result: %v\n", err)
		os.Exit(1)
	}

	if err := os.WriteFile(outputPath, data, 0o644); err != nil {
		fmt.Fprintf(os.Stderr, "error writing %s: %v\n", outputPath, err)
		os.Exit(1)
	}

	fmt.Printf("wrote %d flags → %s\n", len(result), outputPath)
}

func fetchAllFlags(client *http.Client, baseURL, namespace, token string) ([]fliptFlag, error) {
	var all []fliptFlag
	pageToken := ""

	for {
		batch, next, err := fetchPage(client, baseURL, namespace, token, pageToken)
		if err != nil {
			return nil, err
		}
		all = append(all, batch...)
		if next == "" {
			break
		}
		pageToken = next
	}

	return all, nil
}

func fetchPage(client *http.Client, baseURL, namespace, token, pageToken string) ([]fliptFlag, string, error) {
	endpoint := fmt.Sprintf("%s/api/v1/namespaces/%s/flags", baseURL, namespace)

	params := url.Values{}
	params.Set("limit", "200")
	if pageToken != "" {
		params.Set("pageToken", pageToken)
	}

	req, err := http.NewRequest(http.MethodGet, endpoint+"?"+params.Encode(), nil)
	if err != nil {
		return nil, "", fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Accept", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}

	resp, err := client.Do(req)
	if err != nil {
		return nil, "", fmt.Errorf("GET %s: %w", endpoint, err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, "", fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, "", fmt.Errorf("flipt returned %d: %s", resp.StatusCode, body)
	}

	var parsed fliptFlagsResponse
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, "", fmt.Errorf("parse response: %w", err)
	}

	return parsed.Flags, parsed.NextPageToken, nil
}

// marshalSorted produces a JSON object with keys sorted alphabetically
// so that the output file has a stable diff.
func marshalSorted(m map[string]bool) ([]byte, error) {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	buf := []byte("{\n")
	for i, k := range keys {
		v := "false"
		if m[k] {
			v = "true"
		}
		line := fmt.Sprintf("  %q: %s", k, v)
		if i < len(keys)-1 {
			line += ","
		}
		buf = append(buf, []byte(line+"\n")...)
	}
	buf = append(buf, '}', '\n')
	return buf, nil
}

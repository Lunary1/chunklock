# API Key Command Guide

## Overview

The `/chunklock apikey` command allows server administrators to configure OpenAI API keys for the AI-powered chunk cost calculation feature.

## Usage

```
/chunklock apikey <your-openai-api-key>
```

## Command Behavior

1. **Input Processing**: The command accepts the full OpenAI API key as a parameter
2. **Validation**: Validates the key format (starts with `sk-proj-` for project keys)
3. **Storage**: Securely stores the key in the plugin configuration
4. **Confirmation**: Provides feedback on successful configuration

## Important Security Notes

- **Never expose real API keys in documentation or version control**
- **Use placeholder values like `YOUR_API_KEY_HERE` in examples**
- **Store actual keys in secure configuration files excluded from Git**
- **Consider using environment variables for sensitive data**

## Configuration

After setting the API key, enable the OpenAI agent in `config.yml`:

```yaml
openai-agent:
  enabled: true
  api-key: "YOUR_ACTUAL_API_KEY_HERE" # Set via command or config
```

## Troubleshooting

- Ensure the API key format is correct (starts with `sk-proj-` for project keys)
- Verify the key has sufficient OpenAI credits
- Check network connectivity for API calls
- Review server logs for detailed error messages
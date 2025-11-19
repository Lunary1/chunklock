# API Key Command Fix - Issue Resolution

## 🐛 Problem Identified

The `/chunklock apikey` command was failing with the error "Usage: /chunklock apikey <api-key>" even when providing a valid API key.

### Root Cause

The issue was in the **argument parsing logic**. Here's what was happening:

1. **User Input**: `/chunklock apikey sk-proj-EXAMPLE_API_KEY_REDACTED_FOR_SECURITY`

2. **ChunklockCommandManager Processing**:

   - Received `args[]` = `["apikey", "sk-proj-EXAMPLE_API_KEY_REDACTED_FOR_SECURITY"]`
   - **Bug**: Created `subArgs = Arrays.copyOfRange(args, 1, args.length)` which strips the first element
   - Passed to ApiKeyCommand: `subArgs[]` = `["sk-proj-EXAMPLE_API_KEY_REDACTED_FOR_SECURITY"]`

3. **ApiKeyCommand Logic**:
   - **Bug**: Checked `if (args.length < 2)` but should have been `if (args.length < 1)`
   - **Bug**: Used `args[1]` but should have been `args[0]`

## ✅ Solution Implemented

### Code Changes

1. **Fixed argument validation**:

   ```java
   // OLD (WRONG)
   if (args.length < 2) {
       // Error - expected 2 args but subcommand name was already stripped
   }
   String apiKey = args[1]; // Wrong index!

   // NEW (CORRECT)
   if (args.length < 1) {
       // Correct - we need at least 1 arg (the API key)
   }
   // Join all arguments starting from index 0
   ```

2. **Enhanced argument joining**:

   ```java
   StringBuilder apiKeyBuilder = new StringBuilder();
   for (int i = 0; i < args.length; i++) {
       if (i > 0) {
           apiKeyBuilder.append(" ");
       }
       apiKeyBuilder.append(args[i]);
   }
   String apiKey = apiKeyBuilder.toString().trim();
   ```

3. **Fixed tab completions**:

   ```java
   // OLD (WRONG)
   if (args.length == 2) {

   // NEW (CORRECT)
   if (args.length == 1) {
   ```

## 🧪 Testing

The fixed plugin has been compiled and deployed to:
`D:\Servers\Classic\paper-server\plugins\Chunklock.jar`

## ✨ Expected Behavior Now

When you run:

```
/chunklock apikey sk-proj-YOUR_OPENAI_API_KEY_HERE
```

You should see:

```
✅ OpenAI API key updated successfully!
🔑 Key: sk-proj-********5QA
📝 Configuration saved to config.yml
💡 Tip: Use '/chunklock test-openai' to test the API connection
```

## 🔧 Technical Details

- **Files Modified**: `ApiKeyCommand.java`
- **Build Status**: ✅ Successful (114 source files compiled)
- **Deployment**: Plugin automatically deployed to server
- **Restart Required**: Yes (reload the plugin or restart server to use the fixed version)

The command should now work correctly with your OpenAI API key!

# Webhook Support for Gerrit Trigger Plugin

The Gerrit Trigger Plugin now supports receiving events via webhooks as an **alternative to SSH stream-events**. Webhook mode changes only how Jenkins **receives** events from Gerrit - SSH connection is still used for **sending** build results back to Gerrit.

## Understanding the Connection Architecture

The Gerrit-Jenkins connection is **bidirectional**:

```
Gerrit Server
    ↑                ↓
    │                │
SSH │ gerrit review  │ stream-events (SSH mode)
    │ (OUTGOING)     │ (INCOMING)
    │ ALWAYS USED    │
    │                OR
    │                │
    │                │ webhook POST (Webhook mode)
    │                ↓ (INCOMING)
Jenkins
```

**Key Points:**
- **OUTGOING (Jenkins → Gerrit)**: SSH connection **always required** for sending build results via `gerrit review` commands
- **INCOMING (Gerrit → Jenkins)**: Choose **either** SSH stream-events **or** webhook HTTP POST for receiving events
- SSH credentials (hostname, port, username, keyfile) must be configured in **both modes**

Webhook event reception provides several advantages:

- **Lower latency**: Direct HTTP calls instead of SSH stream-events
- **Better scalability**: Reduces load on Gerrit server
- **Enhanced security**: Multiple authentication methods available
- **Firewall friendly**: Uses standard HTTP/HTTPS ports

## Overview

When webhook mode is selected:

- **INCOMING**: Gerrit sends HTTP POST requests directly to Jenkins when events occur (patchset created, comment added, etc.)
- **OUTGOING**: Jenkins uses SSH connection to send `gerrit review` commands back to Gerrit (posting votes and comments)
- The webhook extension converts HTTP requests into the same internal event format used by SSH connections
- All existing trigger configurations work identically

## Architecture

```
Gerrit Server --> Webhook HTTP POST --> Jenkins WebhookEventReceiver --> WebhookEventProcessor --> Existing Trigger Pipeline
```

The webhook functionality integrates seamlessly with the existing plugin architecture:

1. **WebhookEventReceiver**: REST endpoint that receives HTTP POST requests
2. **WebhookEventProcessor**: Converts JSON payloads to GerritEvent objects
3. **WebhookAuthenticator**: Validates requests using multiple security methods
4. **WebhookConfig**: Configuration settings for webhook behavior

## Setup Instructions

### 1. Configure Jenkins

1. Go to "Manage Jenkins" → "Gerrit Trigger"
2. Select your Gerrit server configuration
3. In the "Gerrit Connection Setting" section, you'll see:
   - **SSH Connection (Always Required)**: Configure hostname, SSH port, username, keyfile, and Frontend URL
     - These fields are **always visible** because SSH is required for sending build results in both modes
   - **Event Reception Method**: Choose how Jenkins receives events from Gerrit:
     - **Receive events via SSH stream-events** - Traditional SSH-based event streaming
     - **Receive events via Webhook (HTTP POST)** - New webhook-based event delivery
4. Select "Receive events via Webhook (HTTP POST)"
5. Optionally enable "Enable Webhook Authentication" and configure:
   - Webhook Secret Token
   - HMAC Secret
   - Allowed IP Addresses
   - Logging options
6. Save the configuration

**Important**: SSH credentials (hostname, port, username, keyfile) are **always required** in both modes because Jenkins uses SSH to send build results back to Gerrit via `gerrit review` commands. Only the event reception method changes.

### 2. Configure Gerrit

Add webhook configuration to your Gerrit server. The webhook URL format is:

```
http://your-jenkins-server/gerrit-webhook
```

#### Example Gerrit webhook configuration:

```ini
[plugin "webhooks"]
    url = http://jenkins.example.com/gerrit-webhook
    maxTries = 3
    retryInterval = 5s
    connectionTimeout = 5s
    socketTimeout = 5s
```

### 3. Test the Connection

1. Create a test change in Gerrit
2. Check Jenkins logs for webhook event processing
3. Verify that builds are triggered as expected

## Security Configuration

The webhook extension supports multiple authentication methods that can be used individually or in combination:

### Secret Token Authentication

A shared secret token that Gerrit includes in webhook requests.

**Configuration:**
- Enable "Require Secret Token"
- Set "Webhook Secret Token" to a strong random value
- Configure Gerrit to include this token in the `X-Gerrit-Token` header

**Gerrit Configuration:**
```ini
[plugin "webhooks"]
    url = http://jenkins.example.com/gerrit-webhook
    secret = your-secret-token-here
```

### HMAC Signature Validation

Cryptographic signatures ensure request integrity and authenticity.

**Configuration:**
- Enable "Require HMAC Signature"
- Set "HMAC Secret" to a strong random value  
- Gerrit will include SHA256 HMAC in the `X-Gerrit-Signature` header

**Gerrit Configuration:**
```ini
[plugin "webhooks"]
    url = http://jenkins.example.com/gerrit-webhook
    secret = your-hmac-secret-here
```

### IP Address Whitelisting

Restrict webhook requests to specific IP addresses or subnets.

**Configuration:**
- Add allowed IP addresses in "Allowed IP Addresses"
- Supports individual IPs (192.168.1.10) and CIDR notation (192.168.1.0/24)
- Requests from other IPs will be rejected

## Supported Events

The webhook extension supports the same Gerrit events as SSH connections:

- **patchset-created**: New patchset uploaded
- **comment-added**: Comment or review added
- **change-merged**: Change merged to target branch
- **change-abandoned**: Change abandoned
- **change-restored**: Abandoned change restored
- **ref-updated**: Reference updated (tags, branches)

## Event Format

Gerrit webhook events use JSON format. Example patchset-created event:

```json
{
  "type": "patchset-created",
  "eventCreatedOn": 1609459200,
  "change": {
    "project": "my-project", 
    "branch": "master",
    "id": "I1234567890abcdef",
    "number": "12345",
    "subject": "Example change",
    "owner": {
      "name": "John Doe",
      "email": "john.doe@example.com"
    },
    "url": "https://gerrit.example.com/c/12345"
  },
  "patchSet": {
    "number": "1",
    "revision": "abcdef1234567890",
    "ref": "refs/changes/45/12345/1",
    "uploader": {
      "name": "John Doe", 
      "email": "john.doe@example.com"
    },
    "createdOn": 1609459200
  }
}
```

## Troubleshooting

### Common Issues

**Webhook requests not reaching Jenkins:**
- Verify the webhook URL is correct
- Check network connectivity between Gerrit and Jenkins
- Ensure Jenkins is accessible from Gerrit server

**Authentication failures:**
- Verify secret tokens match between Gerrit and Jenkins
- Check that IP addresses are correctly whitelisted
- Review Jenkins logs for authentication error details

**Events not triggering builds:**
- Confirm webhook events are being received (check Jenkins logs)
- Verify trigger configurations match webhook events
- Ensure webhook authentication is properly configured

### Logging

Enable webhook request logging for debugging:

1. Go to server configuration in Jenkins
2. Enable "Log Webhook Requests"
3. Check Jenkins system logs for detailed webhook processing information

### Monitoring

Monitor webhook performance and reliability:

- Check Jenkins system logs for webhook processing times
- Monitor failed webhook attempts in Gerrit logs
- Set up alerting for webhook authentication failures

## Migration from SSH to Webhook Event Reception

**Important**: You are not migrating the entire SSH connection - only changing how Jenkins **receives** events from Gerrit. SSH credentials remain required for **sending** build results back to Gerrit in both modes.

### What Changes:
- **Event Reception**: From SSH stream-events → HTTP POST webhooks
- **SSH Connection**: Remains active in both modes for sending `gerrit review` commands

### What Stays the Same:
- SSH credentials (hostname, port, username, keyfile)
- Frontend URL configuration
- All trigger configurations
- Build result posting mechanism

### Recommended Migration Steps:

1. **Prepare in a test environment first:**
   - Set up a test Jenkins instance
   - Change event reception method to webhook
   - Test with a non-production Gerrit server
   - Verify all triggers work correctly and build results are posted

2. **Plan the migration window:**
   - Choose a low-activity time for the switch
   - Notify team members of the change
   - Have rollback plan ready

3. **Execute the migration:**
   - In Jenkins, go to Gerrit server configuration
   - **Keep all SSH credentials as-is** (hostname, port, username, keyfile)
   - Change "Event Reception Method" from "SSH stream-events" to "Webhook (HTTP POST)"
   - Configure webhook authentication settings
   - Note the webhook URL: `http://your-jenkins-server/gerrit-webhook`
   - Save the configuration

4. **Configure Gerrit webhooks:**
   - Add webhook plugin configuration to Gerrit
   - Point webhooks to the Jenkins URL
   - Include authentication tokens/secrets as configured
   - Test with a sample change

5. **Verify and monitor:**
   - Create test changes in Gerrit
   - Verify webhook events are received in Jenkins logs
   - **Confirm build results are posted to Gerrit** (votes and comments appear)
   - Monitor for any issues for 24-48 hours

### Rollback Procedure:

If you need to revert to SSH stream-events:

1. Go to Gerrit server configuration in Jenkins
2. Change "Event Reception Method" back to "SSH stream-events"
3. **SSH credentials remain unchanged** (no need to re-enter)
4. Save the configuration
5. Remove webhook configuration from Gerrit

**Note**: SSH credentials are never removed or changed during this process. You are only changing how events are received, not how results are sent.

## Best Practices

### Security
- Use strong, unique secret tokens and HMAC secrets
- Regularly rotate authentication credentials
- Enable IP whitelisting when possible
- Monitor for authentication failures

### Performance  
- Configure appropriate timeout values in Gerrit
- Monitor webhook processing times
- Use webhook logging sparingly in production

### Reliability
- Configure retry logic in Gerrit webhook settings
- Set up monitoring and alerting for webhook failures
- Test webhook functionality during Gerrit/Jenkins updates

## API Reference

### Webhook Endpoint

**URL:** `/gerrit-webhook`
**Method:** POST
**Content-Type:** application/json

**Headers:**
- `X-Gerrit-Token`: Secret token (if configured)
- `X-Gerrit-Signature`: HMAC signature (if configured)

**Response:**
- `200 OK`: Event processed successfully
- `400 Bad Request`: Invalid payload
- `401 Unauthorized`: Authentication failed
- `403 Forbidden`: Webhooks disabled or IP not allowed
- `404 Not Found`: No Gerrit server configured
- `500 Internal Server Error`: Processing error

## Limitations

- RefUpdated events have limited conversion support (future enhancement)
- Webhook authentication requires Gerrit webhook plugin configuration
- IP whitelisting may not work correctly behind certain proxy configurations

## Contributing

The webhook extension is designed to be extensible. Key areas for contribution:

- Additional event type support
- Enhanced authentication methods
- Performance optimizations
- Additional webhook payload formats

For development setup and contribution guidelines, see the main plugin documentation.
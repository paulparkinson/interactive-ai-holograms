# GCP VM Setup Commands Reference

This document contains all commands used to set up the GCP VM instance for the AI Holograms application.

## VM Details
- **Instance Name**: paulpark-aiagent-instance
- **IP Address**: 34.48.146.146
- **Zone**: us-east4-a
- **Project**: adb-pm-prod
- **SSH Key**: c:\Users\paulp\.ssh\ssh-key-2025-10-20.key
- **Username**: ssh-key-2025-10-20
- **Wallet File**: Wallet_PAULPARKDB.zip

## SSH Key Permissions (Windows)

```powershell
# Remove inheritance and set proper permissions for SSH key
icacls "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" /inheritance:r
icacls "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" /grant "$($env:USERNAME):R"

# Verify public key matches server
ssh-keygen -y -f "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key"
```

## Test SSH Connection

```powershell
# Test connection
ssh -i "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" ssh-key-2025-10-20@34.48.146.146 "echo 'Connection successful'"
```

## Upload and Extract Database Wallet

```powershell
# Copy wallet file to VM
scp -i "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" "c:\Users\paulp\Downloads\Wallet_PAULPARKDB.zip" "ssh-key-2025-10-20@34.48.146.146:/home/ssh-key-2025-10-20/"

# Create wallet directory, install unzip, and extract wallet
ssh -i "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" ssh-key-2025-10-20@34.48.146.146 "mkdir -p wallet && mv Wallet_PAULPARKDB.zip wallet/ && cd wallet && sudo apt update && sudo apt install -y unzip && unzip Wallet_PAULPARKDB.zip && ls -la"
```

## Install and Configure VS Code Server

```powershell
# Install code-server on VM
ssh -i "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" ssh-key-2025-10-20@34.48.146.146 "curl -fsSL https://code-server.dev/install.sh | sh"

# Configure code-server (port 8080, no auth for SSH tunnel)
ssh -i "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" ssh-key-2025-10-20@34.48.146.146 "mkdir -p ~/.config/code-server && echo 'bind-addr: 127.0.0.1:8080' > ~/.config/code-server/config.yaml && echo 'auth: none' >> ~/.config/code-server/config.yaml && echo 'cert: false' >> ~/.config/code-server/config.yaml"

# Start code-server in background
ssh -i "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" ssh-key-2025-10-20@34.48.146.146 "nohup code-server > ~/code-server.log 2>&1 &"

# Verify code-server is running
ssh -i "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" ssh-key-2025-10-20@34.48.146.146 "ps aux | grep code-server | grep -v grep && tail -n 5 ~/code-server.log"
```

## Create SSH Tunnel for VS Code Access

```powershell
# Create SSH tunnel (forwards local port 8080 to VM port 8080)
# Run this command and keep it running in a terminal
ssh -i "c:\Users\paulp\.ssh\ssh-key-2025-10-20.key" -L 8080:localhost:8080 -N ssh-key-2025-10-20@34.48.146.146

# After tunnel is established, access VS Code in browser at:
# http://localhost:8080
```

## Wallet File Locations on VM

- **Wallet directory**: `/home/ssh-key-2025-10-20/wallet/`
- **Wallet files**:
  - `tnsnames.ora`
  - `sqlnet.ora`
  - `cwallet.sso`
  - `ewallet.p12`
  - `ewallet.pem`
  - `keystore.jks`
  - `truststore.jks`
  - `ojdbc.properties`
  - `README`

## Notes

- The VM has two user accounts:
  - `ssh-key-2025-10-20` - Created when SSH key was added (used for manual SSH)
  - `paul_parkinson` - Created by Google Cloud based on GCP account (used for browser SSH)
- Each user has their own home directory: `/home/<username>/`
- Code-server is installed and running under `ssh-key-2025-10-20` user
- The SSH tunnel must remain active to access VS Code in the browser

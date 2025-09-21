# Configuration Security Notice

This repository contains template configuration files to protect sensitive information from being committed to the public repository.

## Sensitive Files Excluded

The following types of files are automatically excluded via `.gitignore`:
- `*.p12` - Certificate keystores
- `*.pem`, `*.key`, `*.crt` - SSL certificates and keys  
- `*.wallet` - Oracle wallet files
- `*.env*` - Environment variable files
- `application.yaml` - Spring configuration with potential secrets
- `*.properties` - Properties files that may contain credentials

## Setting Up Configuration

### For Latest Version:
1. Copy `latest-version/src/main/resources/application-template.yaml` to `application.yaml` in the same directory
2. Replace placeholder values with your actual configuration:
   - `${SECURITY_USER_NAME:oracleai}` → your security username
   - `${SECURITY_USER_PASSWORD:changeme}` → your security password
   - `${SSL_KEYSTORE_PASSWORD:changeme}` → your keystore password
   - Configure any database connection strings as needed

### For Older Version:
1. Copy `older-versions/java-version/src/main/resources/application-template.yaml` to `application.yaml` in the same directory
2. Follow the same configuration steps as above

### SSL Certificates:
If you need SSL certificates:
1. Generate or obtain your certificates
2. Place them in the appropriate resources directory
3. Update the application configuration to reference them
4. Ensure certificate files are included in `.gitignore` patterns

## Environment Variables

You can also use environment variables to override configuration:
- `SECURITY_USER_NAME` - Security username
- `SECURITY_USER_PASSWORD` - Security password  
- `SERVER_PORT` - Server port (defaults to 8080)
- `SSL_KEYSTORE_PATH` - Path to SSL keystore
- `SSL_KEYSTORE_PASSWORD` - SSL keystore password
- `SSL_KEY_ALIAS` - SSL key alias

## Important Security Notes

⚠️ **NEVER commit the following to the repository:**
- Actual `application.yaml` files with real credentials
- Certificate files (`.p12`, `.pem`, `.key`, etc.)
- Oracle wallet directories or files
- Any files containing real passwords, API keys, or connection strings

✅ **Safe to commit:**
- Template files (`application-template.yaml`)
- Documentation and README files
- Source code without embedded credentials
- Build scripts and configuration (without secrets)

If you accidentally commit sensitive information, immediately:
1. Remove it from the repository
2. Rotate any compromised credentials
3. Consider the information as potentially exposed
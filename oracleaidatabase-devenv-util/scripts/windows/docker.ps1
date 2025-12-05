# PowerShell version of docker.sh
# Wrapper to run docker commands (for consistency with Linux wrapper)

param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$DockerArgs
)

$ErrorActionPreference = "Stop"

# Simply pass through all arguments to docker
& docker @DockerArgs

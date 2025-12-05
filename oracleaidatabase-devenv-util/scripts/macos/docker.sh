#!/usr/bin/env bash
# Wrapper to run docker commands with the docker group active
exec sg docker -c "docker $*"

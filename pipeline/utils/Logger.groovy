#!groovy

import groovy.json.JsonSlurper

/**
 * Centralized logging
 */
 
@NonCPS
def info(String message) {
    echo "INFO: ${message}"
}

def error(String message) {
    echo "WARNING: ${message}"
}

def debug(String message) {
    if (env.DEBUG)
        echo "DEBUG: ${message}"
}

#!groovy

import groovy.json.JsonSlurper

/**
 * Centralized logging
 */
 
@NonCPS
def info(String message) {
    echo "INFO: ${message}"
 return
}

def error(String message) {
    echo "WARNING: ${message}"
 return
}

def debug(String message) {
    if (env.DEBUG)
        echo "DEBUG: ${message}"
 return
}


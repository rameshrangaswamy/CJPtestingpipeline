#!groovy

import groovy.json.JsonSlurper

/**
 * Centralized logging
 */ 
@NonCPS

def info(String message) {
    echo "INFO: ${message}"
}
return this

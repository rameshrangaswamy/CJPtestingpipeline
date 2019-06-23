#!groovy

import groovy.json.JsonSlurper

/**
 * Centralized logging
 */ 

def info(String message) {
    echo "INFO: ${message}"
}
return this

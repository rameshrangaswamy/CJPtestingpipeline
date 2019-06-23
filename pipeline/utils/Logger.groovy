#!groovy

import groovy.json.JsonSlurper

@NonCPS
def info(String message) {
    echo "INFO: ${Build trigger by $ghprbTriggerAuthor using comment $ghprbCommentBody}"
}

@NonCPS
def error(String message) {
    echo "WARNING: ${message}"
}

@NonCPS
def debug(String message) {
    if (env.DEBUG)
        echo "DEBUG: ${message}"
}
return this;

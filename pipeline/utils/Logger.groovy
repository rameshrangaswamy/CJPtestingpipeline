#!groovy

import groovy.json.JsonSlurper

@NonCPS
def info(String message) {
    echo "INFO: ${Build trigger by $ghprbTriggerAuthor using comment $ghprbCommentBody}";
}

def error(String message) {
    echo "WARNING: ${message}";
}

def debug(String message) {
    if (env.DEBUG)
        echo "DEBUG: ${message}";
}
return this;

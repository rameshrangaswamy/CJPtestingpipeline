/**
 * Centralized logging
 */
def logger(){
info(String) {
    echo "INFO: ${message}";
}

error(String) {
    echo "WARNING: ${message}";
}

debug(String) {
    if (env.DEBUG)
        echo "DEBUG: ${message}";
}
}
return this;

import groovy.util.logging.Slf4j

@Grapes([
    @Grab(group='ch.qos.logback', module='logback-classic', version='1.0.13') 
])

// 
// Classes
// =======

@Slf4j
class StandardGreeting {

    def greet() {
        log.trace "Hello world"
        log.debug "Hello world"
        log.warn  "Hello world"
        log.info  "Hello world"
        log.error "Hello world"
    }
}

@Slf4j
class SpecialGreeting {

    def greet() {
        log.trace "Hello world"
        log.debug "Hello world"
        log.warn  "Hello world"
        log.info  "Hello world"
        log.error "Hello world"
    }
}

@Slf4j
class GreetingRunner {

    def greetings  = [new StandardGreeting(), new SpecialGreeting()]

    def run() {
        log.info "Starting to talk"

        greetings.each {
            it.greet()
        }

        log.info "Finished talking"
    }
}

// 
// Main program
// ============
def runner = new GreetingRunner()

runner.run()

node("NODE_LABEL") 
{
def mavenHome = tool 'maven';
		stage('SCM Checkout')
		{
			checkout scm
		}
	
	withEnv([
                'MAVEN_HOME=' + mavenHome,
                "PATH=${mavenHome}/bin:${env.PATH}"
			]) 
		stage('build & UT')
		{       
				sh "cd /home/rameshrangaswamy1/.jenkins/workspace/PR_PHASE_1"
				sh "mvn clean install"
		}

}





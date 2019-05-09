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
			ws dir("walmart")
			{
				sh "mvn clean install"
			}
		}

}





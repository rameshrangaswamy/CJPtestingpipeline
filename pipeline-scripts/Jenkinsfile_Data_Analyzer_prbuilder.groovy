node("NODE_LABEL") 
{
def antHome
def mavenHome
		stage('SCM Checkout')
		{
			checkout scm
			mavenHome = tool('maven');
		}
	
	withEnv([
                'MAVEN_HOME=' + mavenHome,
                "PATH=${mavenHome}/bin:${env.PATH}"
			]) 
		stage('build & UT')
		{       
			dir("walmart")
			{
				sh "mvn clean install"
			}
		}

}



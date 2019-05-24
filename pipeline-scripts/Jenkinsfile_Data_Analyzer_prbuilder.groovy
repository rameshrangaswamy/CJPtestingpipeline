node("NODE_LABEL") 
{
def mavenHome = tool 'maven';
		stage('SCM Checkout')
		{
			checkout scm
		}
		stage('build & UT')
		{       
			dir('walmart'){
				sh "'${mavenHome}/bin/mvn' clean package"
			}
		}
			stage('sonarAnalysis')
		{       
			dir('walmart'){
				sh "'${mavenHome}/bin/mvn' mvn sonar:sonar \
  					-Dsonar.host.url=http://35.200.203.119:9000 \
  					-Dsonar.login=bc7ed6c23eabd5e5001bcc733194bf9925c85efc"
			}
		}

}





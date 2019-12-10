package com.mediator.main
import com.mediator.common.Commons
import groovy.json.JsonSlurper

class HUBBuild {
   def context = [:]
   def script

   HUBBuild(script, env, params){
     
      this.script = script
      this.context = context
     
      //Assign slack channel
      this.context.slack_channel = "#medtest"
     
      /* Fetch all custom variables*/
      this.context.repo = "Denver_Hub"
      this.context.branch = "master"
      this.context.deploy_env = 'Production'
      this.context.active_core = "Both"
      this.context.build_type = 'Release'
      this.context.build_number = env.BUILD_NUMBER ? env.BUILD_NUMBER : params.BUILD_NUMBER
      this.context.build_name = "HUB-Release-" + "${this.context.build_number}"
      this.context.build_url = env.BUILD_URL ? env.BUILD_URL : params.BUILD_URL
      this.context.current_home = "/wwwroot/configuration/current/"
      this.context.backup_home =  "/srv/isilon/backups/config_backups/"
      this.context.git_home = "/tmp/tmp_git/*"
      this.context.tmp_git_home = "/tmp/tmp_git"
      this.context.x_active_core="100.125.130.61"
      this.context.y_active_core="100.125.130.68"
 
     
      //Assign a git repo
      this.context.project_repo = "https://github.inbcu.com/OnAirSystems/${this.context.repo}.git"
   }
   
   def init(){
     startNotify();
     codeBuild();
     codeDeploy();
     completedNotify();
   }
   
   def startNotify(){
      script.node{
         script.stage('Slack Start Notify'){
         script.println "${this.context.job_name}"
         def message = ":blush: *STARTED*\n_*Denver_Hub Deployment*_ IS INPROGRESS. \n URL: ${this.context.build_url} "
         sendNotification(message)        
         }
      }
   }

      def codeBuild(){
       script.node {
          script.deleteDir()
          script.println "==== Code build Started ==="
          script.stage('Code Build'){
       
           if (this.context.branch != null) {
              try{    
                 script.println "${this.context.build_name} **** ${this.context.branch} ***** ${this.context.tmp_git_home}"
                 script.sh "rm -rf ${this.context.tmp_git_home}"
                 checkoutGitRepo("${this.context.branch}","${this.context.tmp_git_home}","${this.context.project_repo}")          
                 }catch(e){
                     script.println e
                 }                                          
             }else{
                script.println "Please specify branch"            
             }          
          }
             script.println "==== Code build Finished ==="
       }
   }


   def codeDeploy(){
      script.node {
         script.stage('Code Deploy'){
         script.deleteDir()
         script.env.WORKSPACE = script.pwd()
          this.context.script_path = script.pwd()
         script.sh "rsync -avr ${this.context.tmp_git_home} ${this.context.script_path}"
        script.sh "ls ; pwd"

         script.println "=== Starting deployment on HUB-Mediator ==="
        script.println "${this.context.current_home}"
        if (this.context.active_core=="Both"){
         deploy(this.context.x_active_core)
         deploy(this.context.y_active_core)
          }
        else if(this.context.active_core=="X-Core") { 
          deploy(this.context.x_active_core)
        }
        else if(this.context.active_core=="Y-Core")
        {
          deploy(this.context.y_active_core)
        }
        else
        {
          script.println "${this.context.active_core} is not the correct core selection"
        }

         script.println "${this.context.active_core} \n === Deploy function complete ==="    
         }
      }
  }

def completedNotify(){
      script.node{
         script.stage('Slack Complete Notify'){
script.currentBuild.displayName = "${this.context.build_name}"        
script.println "${this.context.job_name}"
def message = ":+1: *SUCCESS*\n_*Denver_Hub Deployment Completed*_. \n Please make your config changes \nURL: ${this.context.build_url}"
         sendNotification(message)        
         }
      }
   }

    //Slack function
  def sendNotification(messages){
      script.node {
     script.slackSend(channel: "${this.context.slack_channel}", message: "${messages}")  
      }
   }
def deploy(core) {
   script.sh '''eval "$(sudo ssh -o StrictHostKeyChecking=no -t "root@"'''+core+''' \' \\
  bkp_curr="$(cd "\'"'''+current_home+'''"\'" && tar -zvcPf ${HOSTNAME}_release_$(date +%d-%m-%Y).tgz *)" \\
  mv_bkp="$(mv "\'"'''+current_home+'''"\'"${HOSTNAME}_release_$(date +%d-%m-%Y).tgz "\'"'''+backup_home+'''"\'")" \\
  ex="$(exit)"\')"

echo "Backup complete"

echo "Merging changes into the current directory structure..."
 
find . -not -name \'*.jar\' -type f -exec dos2unix --keepdate {} \\;

echo "${core}"

sudo rsync -arov -p --chmod=+x  --delete '''+this.context.tmp_git_home+'''/current/* -e \'ssh -o StrictHostKeyChecking=no -t \' root@'''+core+''':'''+current_home+'''
'''
}

 
def checkoutGitRepo(branchName, targetDir, repoURL) {
  script.checkout([$class: 'GitSCM', branches: [[name: branchName]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'WipeWorkspace'], [$class: 'RelativeTargetDirectory', relativeTargetDir: targetDir]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'github-token', url: repoURL]]])
}
   
}

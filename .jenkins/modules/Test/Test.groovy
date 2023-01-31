#!groovy
echo 'Debug 1'
MPLPostStep('always') {
    sh returnStatus: true, script: "docker stop james-mail jwsdp"
    echo "Cleaning up workspace from parent"
    cleanWs()
}
echo 'Debug 2'

MPLPostStep('failure') {
    echo "There are test failures, archiving logs"
    //archiveArtifacts artifacts: 'results/**/*.tar.gz,results/*.tar.gz'
    // since we have mail related issues as well, let's collect james output as well
    //sh "docker ps -f name='james-mail' && docker logs james-mail 2>1| gzip -c > james.log.gz"
    //archiveArtifacts artifacts: 'james.log.gz', allowEmptyArchive: true
}
echo 'Debug 3'

def envConfig = getTCKConfig("${CFG.suiteName}").flatten()
echo 'Debug 4'
echo "Env Config: ${envConfig}"
env.JAVA_HOME = "${CFG.jdk}"
echo 'Debug 5'
withEnv (envConfig) {
    echo 'Debug 6'
    def shellScript
    def junitReportLocation
    // non-tck suites, like CDI and Beanvalidation require different script and their test results are elsewhere
    switch (job) {
        case "activation":
        case "jaf":
            shellScript = """
                                    mkdir results
                                    cd jaf
                                    ./build.sh
                                    ./run.sh
                                """
            junitReportLocation = 'jaf/jaf-tck/results/**/*-junit-report.xml'
            break
        case "cdi":
            shellScript = """
                                    mkdir results
                                    cd cdi
                                    ./build.sh
                                    ./run.sh
                                """
            junitReportLocation = 'cdi/cditck-porting/cdi-tck-report/**/*-junit-report.xml'
            break
        case "beanvalidation":
            shellScript = """
                                    mkdir results
                                    cd bv
                                    ./build.sh
                                    ./run.sh
                                """
            junitReportLocation = 'bv/bvtck-porting/bvtck-report/**/*-junit-report.xml'
            break
        case "di":
            shellScript = """
                                    mkdir results
                                    cd di
                                    ./build.sh
                                    ./run.sh
                                """
            junitReportLocation = 'di/ditck-porting/330tck-report/*-junit-report.xml'
            break
        case "dsol":
        case "debugging":
            shellScript = """
                                    mkdir results
                                    cd dsol
                                    ./build.sh
                                    ./run.sh
                                """
            junitReportLocation = 'dsol/dsol-tck/debugging-tck-junit-report.xml'
            break
        case "javamail-standalone":
        case "mail-standalone":
            shellScript = """
                                    mkdir results
                                    cd mail
                                    ./build.sh
                                    ./run.sh
                                """
            junitReportLocation = 'mail/mail-tck/mail-tck/JTreport/**/*, mail/mail-tck/mail-tck/JTreport-Pluggability/**/*'
            break
        case "jaxb":
        case "xml-binding":
            shellScript = """
                                    mkdir results
                                    cd jaxb
                                    ./build.sh
                                    ./run.sh
                                """
            junitReportLocation = 'jaxb/jaxb-tck/results/junitreports/JAXB-TCK-junit-report.xml'
            break
        default:
            // forward and reverse are keywords that are passed via environment to the script
            def userKeywords = ""
            if (job.endsWith("_forward")) {
                userKeywords = 'USER_KEYWORDS="forward"'
                job = job.replace("_forward", "")
            } else if (job.endsWith("_reverse")) {
                userKeywords = 'USER_KEYWORDS="reverse"'
                job = job.replace("_reverse", "")
            }
            shellScript = """
                                    ${userKeywords} ./run.sh ${job}
                                """
            junitReportLocation = 'cts_home/jakartaeetck/results/junitreports/*.xml'
            break
    }
}

echo "Stage runs at ${env.NODE_NAME}"
echo "Java version is: ${JAVA_HOME}"

// Ensure that the docker daemon is running
sh """if [ "\$(systemctl is-active docker)" = "inactive" ]
                            then 
                                echo "Docker daemon not started - Starting docker container"
                                sudo systemctl start docker 
                            fi"""

sh "nohup bundles/run_server.sh &"
sh "sleep 10"

sh shellScript
// suite should produce relevant TCK result snippet in file stage_
stash allowEmpty: true, includes: 'stage_*', name: env.SAFE_JOB_NAME

def testResult = junit testResults: junitReportLocation
echo "Stage test result ${testResult.passCount} / ${testResult.totalCount}"
if (testResult.failCount > 0) {
    // when tests fail, collect full logs
    archiveArtifacts artifacts: 'results/**/*.tar.gz,results/*.tar.gz'
    // since we have mail related issues as well, let's collect james output as well
    sh "docker ps -f name='james-mail' && docker logs james-mail 2>1| gzip -c > james.log.gz"
    archiveArtifacts artifacts: 'james.log.gz', allowEmptyArchive: true
}
// stop the mailserver container used in tests. It tends to fail after few hours. And also the other container we might have started
sh returnStatus: true, script: "docker stop james-mail jwsdp"

def getTCKConfig(job) {
    def baseURL = "${JENKINS_URL}userContent/tck"
    def tckSpecificConfig = ["BASE_URL=${baseURL}",
                             "SAFE_JOB_NAME=${job.replaceAll('/', '_')}",
                             "TZ=UTC",
                             "JAVA_HOME=${tool params.jdkVer}",
                             "PATH+JAVA=${tool params.jdkVer}/bin"]


    tckSpecificConfig << ["TCK_URL=https://download.eclipse.org/jakartaee/platform/8/jakarta-jakartaeetck-8.0.2.zip",
                          "GLASSFISH_URL=${baseURL}/glassfish-5.1.0.zip",
                          "BV_TCK_BUNDLE_URL=${baseURL}/beanvalidation-tck-dist-2.0.5.zip"]

    if (job == "dsol" || job == "debugging") {
        tckSpecificConfig << ["TCK_BUNDLE_BASE_URL=https://download.eclipse.org/jakartaee/debugging/1.0/"]
    }
    return tckSpecificConfig
}
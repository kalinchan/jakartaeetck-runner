#!groovy
MPLModule('Build', CFG)
dir('appserver/distributions/payara-web/target') {
    archiveArtifacts artifacts: 'payara-web.zip', fingerprint: true
}
OUT.'build_info.build.version' = payaraVersion
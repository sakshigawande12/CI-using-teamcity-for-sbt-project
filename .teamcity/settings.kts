import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.dockerCommand
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2020.1"

project {

    buildType(KnolwledgePortal)
}

object KnolwledgePortal : BuildType({
    name = "knolwledge-portal"

    params {
        password("env.EMAIL_USER", "credentialsJSON:b6775577-cec5-4c97-b4ad-d7073c6264e5", display = ParameterDisplay.HIDDEN)
        password("rgistrationKey", "credentialsJSON:3f0215fe-05d2-4fa8-ac55-c6cd5cfc285e", display = ParameterDisplay.HIDDEN)
        password("env.EMAIL_PASSWORD", "credentialsJSON:1c6d109f-80b1-4afa-a7f0-537d8970ab7d", display = ParameterDisplay.HIDDEN)
        password("git-password", "credentialsJSON:e3aad327-f730-4067-876d-7b703293c4b3", display = ParameterDisplay.HIDDEN)
        password("env.MONGO_URL", "credentialsJSON:dae7ba41-7409-4ed0-a8b0-a726fe91a836", display = ParameterDisplay.HIDDEN)
        password("git-user", "credentialsJSON:68be21e6-284e-48f2-b6ff-d3262aeac7e2", display = ParameterDisplay.HIDDEN)
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        step {
            name = "compilation"
            type = "SBT"
            param("sbt.args", "clean compile")
        }
        step {
            name = "testing"
            type = "SBT"
            param("sbt.args", "clean test")
        }
        step {
            name = "packaging"
            type = "SBT"
            param("sbt.args", "assembly")
        }
        step {
            name = "coverage-report"
            type = "SBT"
            param("sbt.args", "coverage test coverageReport")
        }
        step {
            name = "cpd-report"
            type = "SBT"
            param("sbt.args", "cpd")
        }
        step {
            name = "scalastyle-report"
            type = "SBT"
            param("sbt.args", "scalastyle")
        }
        script {
            name = "coverage-report-to-codesquad"
            scriptContent = """curl -X PUT -F "projectName=<project-name>" -F "moduleName=rest-endpoint" -F "organisation=knoldus inc" -F "file=@/opt/buildagent/work/d7f0968a5648da59/target/scala-2.12/sbt-1.0/scoverage-report/scoverage.xml" -F "registrationKey=%rgistrationKey%" https://www.getcodesquad.com/api/add/reports"""
        }
        script {
            name = "cpd-to-codesquad"
            scriptContent = """curl -X PUT -F "projectName=<project-name>" -F "moduleName=rest-endpoint" -F "organisation=knoldus inc" -F "file=@/opt/buildagent/work/d7f0968a5648da59/target/scala-2.12/sbt-1.0/cpd/cpd.xml" -F "registrationKey=%rgistrationKey%" https://www.getcodesquad.com/api/add/reports"""
        }
        script {
            name = "scalastyle-to -codesquad"
            scriptContent = """curl -X PUT -F "projectName=<project-name>" -F "moduleName=rest-endpoint" -F "organisation=knoldus inc" -F "file=@/opt/buildagent/work/d7f0968a5648da59/target/scalastyle-result.xml" -F "registrationKey=%rgistrationKey%" https://www.getcodesquad.com/api/add/reports"""
        }
        dockerCommand {
            name = "build docker image"
            commandType = build {
                source = file {
                    path = "Dockerfile"
                }
                namesAndTags = "knolx-rest:0.1"
                commandArgs = "--pull"
            }
        }
        script {
            name = "login to github package"
            scriptContent = "docker login docker.pkg.github.com --username %git-user% --password %git-password%"
        }
        script {
            name = "tag docker image"
            scriptContent = "docker tag knolx-rest:0.1 docker.pkg.github.com/<module-name>/<project-name>/knolx-rest:0.1"
        }
        script {
            name = "push docker image"
            scriptContent = "docker push docker.pkg.github.com/<module-name>/<project-name>/knolx-rest:0.1"
        }
    }

    triggers {
        vcs {
        }
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "${DslContext.settingsRoot.id}"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:e3aad327-f730-4067-876d-7b703293c4b3"
                }
            }
        }
    }
})

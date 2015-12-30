def arches = [
	'arm64',
	'armel',
	'armhf',
	'ppc64le',
	's390x',
]

def images = [
	'buildpack-deps',
	'busybox',
	'debian',
	'gcc',
	'hello-world',
	'perl',
	'php',
	'python',
	'redis',
	'ubuntu',
]

for (arch in arches) {
	archImages = images.collect { arch + '/' + it }
	freeStyleJob("docker-${arch}-docs") {
		logRotator { daysToKeep(30) }
		label("docker-${arch}")
		scm {
			git {
				remote { url('https://github.com/docker-library/docs.git') }
				branches('*/master')
				clean()
			}
		}
		triggers {
			upstream("docker-${arch}-perl", 'UNSTABLE')
			scm('H/6 * * * *')
		}
		wrappers {
			colorizeOutput()
			credentialsBinding {
				usernamePassword('USERNAME', 'PASSWORD', "docker-hub-buildslave${arch}")
			}
		}
		steps {
			shell("""\
prefix='${arch}'

sed -i "s!^FROM !FROM \$prefix/!" Dockerfile
cat > .template-helpers/generate-dockerfile-links-partial.sh <<-'EOF'
	#!/bin/bash
	set -e
	
	echo '** THESE IMAGES ARE VERY, VERY EXPERIMENTAL; THEY ARE PROVIDED ON A BEST-EFFORT BASIS WHILE [docker/docker#15866](https://github.com/docker/docker/issues/15866) IS STILL IN-PROGRESS -- PLEASE DO NOT USE THEM FOR ANYTHING SERIOUS OR IMPORTANT ** (aside from the important task of CI for testing Docker itself, which is one of their primary purposes for existence)'
	echo
	
	echo "This image is built from the source of the [official image of the same name (\\`\$1\\`)](https://hub.docker.com/_/\$1/).  Please see that image's description for links to the relevant \\`Dockerfile\\`s."
	echo
	
	echo 'If you are curious about specifically how this image differs, see [the Jenkins Groovy DSL scripts in the `tianon/jenkins-groovy` GitHub repository](https://github.com/tianon/jenkins-groovy/tree/master/dsl/docker-multiarch/images), which are responsible for creating the Jenkins jobs which build them.'
	echo
EOF
cat > .template-helpers/user-feedback.md <<-'EOF'
	If you have issues with or suggestions for this image, please file them as issues on the [`tianon/jenkins-groovy` GitHub repository](https://github.com/tianon/jenkins-groovy/issues).
EOF
sed -ri "s!^docker pull !#&!; s!^(docker run --rm|docker images) !\\1 \$prefix/!" hello-world/update.sh
./update.sh \\
	${images.join(" \\\n\t")}

docker build -t docker-library-docs .
test -t 1 && it='-it' || it='-i'
set +x
docker run "\$it" --rm -e TERM \\
	--entrypoint './push.pl' \\
	docker-library-docs \\
	--username "\$USERNAME" \\
	--password "\$PASSWORD" \\
	--batchmode \\
		${archImages.join(" \\\n\t\t")}
""")
		}
	}
}
import vars.multiarch

for (arch in multiarch.allArches()) {
	meta = multiarch.meta(getClass(), arch)

	freeStyleJob(meta.name) {
		description(meta.description)
		logRotator { daysToKeep(30) }
		label(meta.label)
		scm {
			git {
				remote { url('https://github.com/docker-library/rails.git') }
				branches('*/master')
				extensions {
					cleanAfterCheckout()
				}
			}
		}
		triggers {
			upstream("docker-${arch}-php", 'UNSTABLE')
			scm('H H/6 * * *')
		}
		wrappers { colorizeOutput() }
		steps {
			shell(multiarch.templateArgs(meta, ['dpkgArch']) + '''
sed -i "s!^FROM !FROM $prefix/!" Dockerfile */Dockerfile

case "$dpkgArch" in
	s390x)
		sed -i "s/nodejs//g" Dockerfile */Dockerfile	
esac

latest="$(./generate-stackbrew-library.sh | awk '$1 == "latest:" { print $3; exit }')"
docker build -t "$repo" .
docker build -t "$repo-onbuild" onbuild/
docker tag -f "$repo" "$repo"
docker tag -f "$repo-onbuild" "$repo-onbuild"

''' + multiarch.templatePush(meta))
		}
	}
}
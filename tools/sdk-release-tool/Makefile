PYTHON_BIN ?= python3

REALMS=dev stage prod
CREDENTIALS=$(REALMS:%=cdn-sdki.%.json)

ifeq ($(shell uname -s),Linux)
	# On linux, you can't easily install the gnomekeyring python
	# module into a venv, so use the system-supplied one.
	VENV_OPTS := '--system-site-packages'
endif

# Do not prompt if user wants to use environment variables instead of json files
ifeq ($(origin SKIP_CREDENTIALS), undefined)
install: venv $(CREDENTIALS)
else
install: venv
endif

clean:
	rm -rf venv

cdn-sdki.%.json:
	@echo; \
	export REALM=$$(echo $@ | cut -d . -f 2); \
	echo "Please provide the AWS access key for cdn-sdki in ($${REALM})."; \
	echo; \
	/bin/echo -n 'AWS_ACCESS_KEY_ID: '; \
	read AWS_ACCESS_KEY_ID; \
	/bin/echo -n 'AWS_SECRET_ACESS_KEY: '; \
	read AWS_SECRET_ACCESS_KEY; \
	echo '{' >$@; \
	echo '  "UserName": "cdn-sdki",' >>$@; \
	echo "  \"AccessKeyId\": \"$${AWS_ACCESS_KEY_ID}\"," >>$@; \
	echo "  \"SecretAccessKey\": \"$${AWS_SECRET_ACCESS_KEY}\"" >>$@; \
	echo '}' >>$@; \
	echo; \
	echo 'The access key has been saved to $@.'; \
	echo

test: venv
	./venv/bin/nosetests tests

venv: requirements.txt
	set -x
	which pip3 || (curl https://bootstrap.pypa.io/pip/get-pip.py | python3)
	$(PYTHON_BIN) -m pip install virtualenv --user
	$(PYTHON_BIN) -m virtualenv venv
	./venv/bin/pip install -r requirements.txt --cache-dir /tmp/pipcache
	touch venv

.PHONY: clean install test

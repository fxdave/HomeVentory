.ONESHELL: # keep cd -ed dirs
.SHELLFLAGS = -ec # stop a recipe at its first failing command

PROD_COMPOSE = docker-compose -f compose.prod.yml -f compose.prod.override.yml

start:
	docker-compose up -d --remove-orphans

stop:
	docker-compose down

update:
	git pull
	docker-compose stop
	docker-compose run --rm front npm i
	docker-compose run --rm back npm i
	docker-compose run --rm back npx prisma migrate dev
	make build
	make start

prod-start: prod-config
	$(PROD_COMPOSE) up -d --remove-orphans

prod-stop: prod-config
	$(PROD_COMPOSE) down

prod-update: prod-config
	git pull
	$(PROD_COMPOSE) stop
	docker-compose run --rm front npm i
	docker-compose run --rm back npm i
	docker-compose run --rm back sh -c "npx prisma migrate deploy && npx prisma generate"
	make build
	make prod-start

install: env prod-config
	docker-compose run --rm front npm i
	docker-compose run --rm back npm i
	docker-compose run --rm back sh -c "npx prisma migrate deploy && npx prisma generate"
	make build

env:
	[ -f back/.env ] && exit 0 # keep the existing JWT_SECRET
	cp back/.env.example back/.env
	export REPLACE="\"$$(cat /dev/random | head -c 50 | base64)\""
	export ESCAPED_REPLACE=$$(printf '%s\n' "$$REPLACE" | sed -e 's/[\/&]/\\&/g')
	sed -i -e "s|JWT_SECRET=|JWT_SECRET=$${ESCAPED_REPLACE}|" back/.env

prod-config:
	[ -f compose.prod.override.yml ] || cp compose.prod.override.yml.sample compose.prod.override.yml

build:
	make build-back
	make build-front

build-back:
	docker-compose run --rm back npm run build
	
build-front:
	docker-compose run --rm front sh -c "npx panda generate && npm run build"

build-front-apk: build-front
	cd front
	npx cap sync android
	cd android
	./gradlew assembleDebug
# Release APK, built the same way F-Droid builds it (see docs/FDROID.md).
# Runs on the host (not in Docker): needs Node 24, JDK 21 and the Android SDK.
# Signed when front/android/keystore.properties exists, unsigned otherwise.
build-front-apk-release:
	cd front
	npm ci
	npx panda codegen
	npx vite build
	npx cap sync android
	cd android
	./gradlew --no-daemon clean assembleRelease
	ls app/build/outputs/apk/release/

install-front-apk: build-front
	cd front
	npx cap sync android
	cd android
	./gradlew installDebug

.PHONY: *

.PHONY: check test pitest run compose-up compose-down docker-build security-scan

check:
	./gradlew check

test:
	./gradlew test

pitest:
	./gradlew pitest

run:
	./gradlew bootRun

compose-up:
	docker compose up -d --build

compose-down:
	docker compose down --remove-orphans

docker-build:
	docker build -t teste-api-spring-egsys:local .

security-scan:
	gitleaks detect --source . --redact
	trivy fs --severity HIGH,CRITICAL --exit-code 1 .
	semgrep scan --config p/ci

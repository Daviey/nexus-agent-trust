.PHONY: help up down test logs clean status certs

POCS = 01-http-baseline 02-https-connect 03-plugin 04-mtls 05-wrappers 06-full
POC  ?= 01-http-baseline

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## Start a PoC (POC=06-full, defaults to 01-http-baseline)
	docker-compose -f $(POC)/docker-compose.yml up -d --build

down: ## Stop a PoC and remove volumes (POC=06-full)
	docker-compose -f $(POC)/docker-compose.yml down -v --remove-orphans

test: ## Show test results for a PoC (POC=06-full)
	@echo "=== $(POC) test output ==="
	@docker-compose -f $(POC)/docker-compose.yml logs tester 2>/dev/null || \
		docker-compose -f $(POC)/docker-compose.yml logs 2>/dev/null | tail -30

logs: ## Tail logs for a PoC (POC=06-full)
	@echo "Tailing $(POC) logs (Ctrl+C to stop)..."
	@docker-compose -f $(POC)/docker-compose.yml logs -f

status: ## Show status of all PoC containers
	@for p in $(POCS); do \
		running=$$(docker-compose -f $$p/docker-compose.yml ps -q 2>/dev/null | wc -l); \
		if [ $$running -gt 0 ]; then \
			echo "  $$p: \033[32mrunning\033[0m ($$running containers)"; \
		else \
			echo "  $$p: \033[90mstopped\033[0m"; \
		fi; \
	done

clean: ## Stop and remove all PoC containers and volumes
	@for p in $(POCS); do \
		echo "cleaning $$p..."; \
		docker-compose -f $$p/docker-compose.yml down -v --remove-orphans 2>/dev/null; \
	done

certs: ## Regenerate self-signed certs for HTTPS/mTLS/full PoCs
	@for d in 02-https-connect 04-mtls 06-full; do \
		if [ -f $$d/certs/generate.sh ]; then \
			echo "generating certs for $$d..."; \
			sh $$d/certs/generate.sh; \
		fi; \
	done

all: ## Run all PoCs sequentially and report results
	@sh run-all.sh

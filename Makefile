start:
	docker network create chatwatcher-network || true
	docker build -t chatwatcher-recognizer recognizer

	docker-compose up --build -d

stop:
	docker-compose down || true
	docker network rm chatwatcher-network || true

attach:
	docker-compose exec chatwatcher /bin/sh

logs:
	docker-compose logs -f
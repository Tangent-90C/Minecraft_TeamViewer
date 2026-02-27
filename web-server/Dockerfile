FROM astral/uv:python3.13-alpine

WORKDIR /app

COPY ./src/* ./pyproject.toml /app/

RUN uv sync

EXPOSE 8765

CMD ["uv", "run", "main.py"]
CREATE TABLE tb_tutor (
    id    INTEGER PRIMARY KEY,
    nome  VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    ativo BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE tb_veterinario (
    id    INTEGER PRIMARY KEY,
    nome  VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    ativo BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE tb_pet (
    id       INTEGER PRIMARY KEY,
    nome     VARCHAR(100) NOT NULL,
    especie  VARCHAR(20)  NOT NULL,
    tutor_id INTEGER      NOT NULL,
    ativo    BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE tb_agendamento (
    id             SERIAL PRIMARY KEY,
    tutor_id       INTEGER     NOT NULL REFERENCES tb_tutor (id),
    pet_id         INTEGER     NOT NULL REFERENCES tb_pet (id),
    veterinario_id INTEGER     NOT NULL REFERENCES tb_veterinario (id),
    dia_hora       TIMESTAMP   NOT NULL,
    status         VARCHAR(20) NOT NULL
);
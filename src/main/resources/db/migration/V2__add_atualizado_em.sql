-- quando o ultimo evento de cadastro (ocorridoEm) foi aplicado. Usado para ignorar eventos
-- antigos reentregues pelo Kafka. Fica NULL nos registros que ja existiam: o proximo evento aplica.
ALTER TABLE tb_tutor       ADD COLUMN atualizado_em TIMESTAMP;
ALTER TABLE tb_veterinario ADD COLUMN atualizado_em TIMESTAMP;
ALTER TABLE tb_pet         ADD COLUMN atualizado_em TIMESTAMP;

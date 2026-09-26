# 💈 Rede Barbearias — API

Backend de gestão completa para uma **rede de barbearias com várias unidades**: agenda por barbeiro, agendamento online, clientes e fidelidade, clube de assinatura, estoque e PDV, financeiro com comissões e notificações automáticas.

**Demo:** [site + painel](https://rede-barbearias-api-frontend.vercel.app) · [API / Swagger](https://rede-barbearias-api.onrender.com/swagger-ui.html) (plano grátis: a primeira requisição pode levar ~1 min para acordar)
**Frontend:** [rede-barbearias-api-frontend](https://github.com/EriveltonArantes/rede-barbearias-api-frontend)

| Conta demo | Senha | Vê |
|---|---|---|
| `admin` | `admin123` | rede inteira |
| `gerente` | `gerente123` | unidade Savassi |
| `recepcao` | `recepcao123` | unidade Centro |
| `barbeiro` | `barbeiro123` | a própria agenda e comissão |
| `cliente` | `cliente123` | horários, pontos e clube |

## Stack
Java 21 · Spring Boot 3.4 · Spring Security + JWT · Spring Data JPA (H2 / PostgreSQL) · Bean Validation · Spring Mail · springdoc-openapi · ZXing (QR Code) · Lombok · Docker

## Funcionalidades
- **Agenda** por barbeiro com checagem de conflito (lock pessimista contra agendamento simultâneo), horário de funcionamento, dias de trabalho, folgas e bloqueios; disponibilidade em slots de 15 min.
- **Agendamento online sem login** com código de consulta, cancelamento validado pelo telefone, cupom de desconto e "sem preferência" (distribui pro barbeiro livre menos ocupado). Rate limit por IP nas rotas públicas e no login.
- **Fechamento do atendimento**: pagamento (Pix, dinheiro, cartão), desconto extra, uso do **clube de assinatura** ou **resgate de fidelidade**, cálculo automático de **comissão**.
- **Pix BR Code** (padrão EMV do Banco Central) com CRC16 e QR Code gerado no servidor.
- **Clientes**: ficha com histórico, gasto total, ticket médio, faltas, serviço favorito.
- **Clube de assinatura**: planos, ciclos mensais, renovação, suspensão automática de inadimplentes (job diário).
- **Estoque e PDV**: entradas, ajustes de inventário, uso interno, venda com baixa de estoque, estorno e comissão por produto.
- **Financeiro**: DRE simplificado, série diária por fonte de receita, caixa do dia, folha de comissões, contas a pagar.
- **Notificações automáticas**: confirmação ao agendar, lembrete no dia, aviso de alteração/cancelamento e pedido de avaliação. Canais plugáveis: e-mail (SMTP) e WhatsApp oficial (Meta Cloud API, modelos prontos). Envio assíncrono após o commit, sem duplicar.
- **Segurança**: papéis ADMIN / GERENTE / RECEPCAO / BARBEIRO / CLIENTE com escopo por unidade, auditoria de ações sensíveis, upload com verificação de tipo real do arquivo.
- **Seed de demonstração** realista (3 unidades, 7 barbeiros, 600 clientes, ~60 dias de histórico relativo à data atual).

## Rodando
```bash
mvn spring-boot:run          # H2 em memória + dados de demonstração em http://localhost:8080
mvn test                     # 16 testes de integração (regras de negócio e notificações)
docker build -t rede-barbearias-api . && docker run -p 8080:8080 rede-barbearias-api
```

## Variáveis de ambiente (produção)
| Variável | Para quê |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL (sem elas: H2 em memória) |
| `JWT_SECRET` | segredo do token (≥ 32 caracteres) |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | admin inicial |
| `APP_SEED` | `false` para não criar dados de demonstração |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM` | e-mails automáticos (ex.: Brevo grátis) |
| `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` | WhatsApp oficial (modelos em `MensagemFactory.MODELOS`) |
| `PIX_CHAVE`, `PIX_NOME`, `PIX_CIDADE` | recebedor do Pix |
| `SITE_URL` | links usados nas mensagens |

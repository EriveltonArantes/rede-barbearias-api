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
- **Notificações automáticas**: confirmação ao agendar, lembrete no dia, **lembrete 1 hora antes**, aviso de alteração/cancelamento e pedido de avaliação com a **cartela fidelidade** (✅✅✅⭕⭕…). Canais plugáveis: e-mail (SMTP) e WhatsApp oficial (Meta Cloud API, modelos prontos). Envio assíncrono após o commit, sem duplicar.
- **Atendimento automático no WhatsApp**: o cliente escreve no número da barbearia e recebe na hora a saudação com o link de agendamento (ou, com tudo fechado, "voltamos amanhã às 9h" + link); se já tem horário, recebe o horário, o link pra ver/cancelar e a cartela. No lembrete, os botões **Confirmo / Preciso cancelar** (ou responder 1 / 2) atualizam a agenda sozinhos — cancelar pelo WhatsApp vale mesmo em cima da hora, pra liberar a cadeira. **PARAR** desliga as mensagens automáticas daquele número (VOLTAR religa). Webhook com verificação de assinatura da Meta, sem repetir resposta, textos editáveis e simulador no painel. Funciona com o número que a barbearia já usa no app WhatsApp Business (coexistência da Meta).
- **Confirmação de presença** também pela página "meu horário" (link do e-mail).
- **Sinal por Pix** (reduz falta): no agendamento online de fim de semana, de quem já faltou, ou sempre — a rede escolhe. Descontado no dia; cancelou com antecedência, volta; faltou ou cancelou em cima da hora, fica com a barbearia e entra no financeiro. Pix estático (recepção confere) ou **Mercado Pago** com confirmação automática por webhook (consulta o pagamento na API antes de confirmar) e rotina que re-confere a cada 5 min.
- **Lista de espera**: dia lotado → o cliente deixa o contato; quando alguém cancela, os primeiros da fila que cabem no horário liberado recebem o link já preenchido.
- **Aniversário e convite de retorno** automáticos, com cupom pessoal de uso único, só pra quem consentiu (LGPD).
- **LGPD**: política de privacidade no site, consentimento de promoções separado dos lembretes, cliente baixa os próprios dados (JSON) e pede exclusão (anonimização que preserva o financeiro).
- **Segurança**: papéis ADMIN / GERENTE / RECEPCAO / BARBEIRO / CLIENTE com escopo por unidade, auditoria de ações sensíveis, upload com verificação de tipo real do arquivo.
- **Seed de demonstração** realista (3 unidades, 7 barbeiros, 600 clientes, ~60 dias de histórico relativo à data atual).

## Rodando
```bash
mvn spring-boot:run          # H2 em memória + dados de demonstração em http://localhost:8080
mvn test                     # 61 testes (regras, notificações, WhatsApp, sinal, lista de espera, relacionamento, LGPD)
docker build -t rede-barbearias-api . && docker run -p 8080:8080 rede-barbearias-api
```

## Variáveis de ambiente (produção)
| Variável | Para quê |
|---|---|
| `DATABASE_URL` | PostgreSQL — pode colar a URL do Neon/Render como vem (`postgresql://usuario:senha@host/banco`); sem ela: H2 em memória |
| `JWT_SECRET` | segredo do token (≥ 32 caracteres) |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | admin inicial |
| `APP_SEED` | `false` para não criar dados de demonstração |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM` | e-mails automáticos (ex.: Brevo grátis) |
| `WHATSAPP_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` | WhatsApp oficial (modelos em `MensagemFactory.MODELOS`) |
| `WHATSAPP_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET` | webhook `/api/whatsapp/webhook` (resposta automática) |
| `MERCADOPAGO_ACCESS_TOKEN` | sinal por Pix com confirmação automática (sem ele: Pix estático e a recepção confere) |
| `LEMBRETE_HORA`, `LEMBRETE_ANTES_MINUTOS` | hora do lembrete do dia (padrão 7) e antecedência do 2º lembrete (padrão 60, `0` desliga) |
| `PIX_CHAVE`, `PIX_NOME`, `PIX_CIDADE` | recebedor do Pix |
| `SITE_URL` | links usados nas mensagens |

## Colocar em produção pra uma barbearia de verdade
1. **Banco**: crie um projeto grátis no [Neon](https://neon.tech) (região São Paulo) e copie a *connection string*.
2. **Render** → serviço `rede-barbearias-api` → *Environment*:
   - `DATABASE_URL` = a connection string do Neon, do jeito que veio;
   - `APP_SEED=false` (começa vazio, só com o admin) e troque `ADMIN_PASSWORD`;
   - `SITE_URL` = endereço do site (links das mensagens).
3. **Plano**: mude de *Free* pra *Starter*. No free o servidor dorme sem acesso e os lembretes não saem.
4. **WhatsApp**: painel → Notificações → "Como ligar o WhatsApp oficial" (número que a barbearia já usa, modelos com botões, webhook).
5. Faça login como admin, cadastre unidades, barbeiros e serviços, e divulgue o link `/#/agendar`.
6. Em **Regras e automações** ajuste o sinal, a lista de espera, aniversário/retorno e preencha razão social, CNPJ e e-mail de privacidade.

As tabelas são criadas e atualizadas sozinhas na primeira partida (`ddl-auto=update`); as colunas acrescentadas nesta versão vêm com valor padrão, pra atualizar o sistema sem quebrar um banco que já tem dados.

## Pix automático (Mercado Pago) — opcional
Sem isso o sinal já funciona: o cliente paga o Pix com a chave da unidade e a recepção clica em "Recebi o Pix".
Pra confirmar sozinho:
1. Crie a conta em [mercadopago.com.br](https://www.mercadopago.com.br) (a da barbearia) e, em *Seu negócio → Configurações → Credenciais de produção*, copie o **Access Token**.
2. No Render: `MERCADOPAGO_ACCESS_TOKEN` = o token. O endereço do webhook é informado em cada cobrança automaticamente (`/api/pagamentos/mercadopago/webhook`).
3. Em **Regras e automações**, se quiser, ligue "sem pagamento no prazo, cancelar sozinho".

A taxa do Mercado Pago pro Pix é cobrada da barbearia (confira a tarifa atual no site deles).

## Aviso se o sistema cair (monitor grátis)
1. Crie uma conta grátis no [UptimeRobot](https://uptimerobot.com).
2. *Add New Monitor* → tipo **HTTP(s)**, URL `https://SEU-SERVICO.onrender.com/actuator/health`, intervalo **5 minutos**.
3. Em *Alert contacts*, coloque seu e-mail (e o app deles no celular, pra receber push).

Você recebe um aviso quando a API parar de responder e outro quando voltar. Bônus: o acesso a cada 5 minutos também impede que
o plano grátis do Render "durma" — mas o plano pago continua sendo o recomendado pra uma barbearia de verdade (o grátis reinicia e tem limite de horas por mês).

## Endereço próprio (ex.: barbeariadafulana.com.br)
1. Compre o domínio no [registro.br](https://registro.br) (~R$ 40/ano).
2. Na Vercel: projeto `rede-barbearias-api-frontend` → *Settings → Domains* → adicione `barbeariadafulana.com.br` e `www.barbeariadafulana.com.br`.
3. No registro.br, em *DNS* do domínio, crie os registros que a Vercel mostrar (normalmente `A` → `76.76.21.21` pro domínio e `CNAME www` → `cname.vercel-dns.com`). Leva de minutos a algumas horas pra propagar; o HTTPS é automático.
4. No Render: `SITE_URL=https://barbeariadafulana.com.br` — assim os links das mensagens (confirmação, lembrete, Pix, avaliação) já saem com o endereço novo.
5. (Opcional) API em `api.barbeariadafulana.com.br`: Render → serviço → *Settings → Custom Domain*, crie o `CNAME` que ele indicar, e troque `VITE_API_URL` na Vercel + a URL do webhook na Meta.

O CORS da API já aceita qualquer origem, então trocar o endereço do site não exige mudança no código.


## Quanto custa pra rodar (estimativa, set/2026)
| Item | Onde | Por mês | Por ano |
|---|---|---|---|
| API (back) | Render — plano Starter (sempre ligado) | US$ 7 | US$ 84 |
| Site + painel (front) | Render — Static Site | grátis | grátis |
| Banco de dados | Neon — plano grátis | grátis | grátis |
| Domínio próprio (opcional) | registro.br | — | ~R$ 40 |
| **Total** | | **~US$ 7** | **~US$ 84 + domínio ≈ R$ 520/ano** |

- Em reais depende do dólar + IOF de cartão internacional (~3,5%): com dólar a R$ 5,00 ≈ R$ 435/ano; a R$ 5,50 ≈ R$ 480; a R$ 6,00 ≈ R$ 520 (sem o domínio).
- **Front na Vercel:** o plano grátis (Hobby) é pra uso pessoal/não comercial; pra barbearia de verdade, mover o front pro Render Static Site (grátis e aceita uso comercial) em vez de pagar o Vercel Pro (~US$ 20/mês).
- **Fora do valor fixo:** mensagens do WhatsApp que a barbearia inicia (lembretes, aniversário, retorno) custam centavos cada na Meta; responder quem escreveu primeiro (a resposta automática) é grátis. Pix pelo Mercado Pago tem taxa por transação.
- Preços mudam: confira no site de cada serviço na hora de contratar.

## Resposta automática no WhatsApp — como funciona
Cliente manda "oi" no número da barbearia → a Meta entrega a mensagem junto com o **nome do perfil do WhatsApp** → o sistema responde na hora:

> Olá, João! 💈 Seja bem-vindo(a) à Rede Barbearias.
> Pra agendar seu horário é rapidinho 👇
> https://barbeariadafulana.com.br/#/agendar

- O link sai com o endereço de `SITE_URL` (com domínio próprio, já vai o domínio).
- Se o número já é de um cliente cadastrado (inclusive quando a Meta manda o celular sem o 9), usa o nome do cadastro e acrescenta o próximo horário, o link pra ver/cancelar e a cartela de fidelidade.
- Perfil sem nome → "Olá!". Fora do horário de todas as unidades → "estamos fechados, voltamos amanhã às 9h" + link.
- Responde de novo o mesmo número só depois do intervalo configurado (padrão 12h); fora isso a conversa segue com a equipe no app do WhatsApp Business.

## Próximos passos
**Pra colocar no ar de verdade (depende do dono):**
1. Neon (grátis) → `DATABASE_URL` no Render. Sem isso o banco é H2 em memória e **cada reinício apaga os dados**.
2. Render → plano Starter (sem ele o servidor dorme e os lembretes não saem).
3. Mover o front da Vercel pro Render Static Site (uso comercial).
4. WhatsApp Business + Meta (número que a barbearia já usa), modelos de mensagem aprovados — passo a passo no painel em Notificações.
5. E-mail (Brevo grátis) — passo a passo no painel.
6. Opcionais: Mercado Pago (Pix automático), UptimeRobot (aviso de queda), domínio próprio.
7. Painel → Regras e automações: razão social, CNPJ, e-mail de privacidade; cadastrar unidades/barbeiros/serviços reais com `APP_SEED=false`.

**Melhoria combinada — formulário já preenchido a partir do WhatsApp:**
hoje o nome vem do WhatsApp só pra mensagem; no site a pessoa ainda digita nome e celular. Plano: cada resposta automática leva um link com um **código temporário** (ex.: `#/agendar?c=K7M2QX9A`, vale algumas horas, uso único); o site troca o código pelo nome e celular e abre o formulário preenchido — o cliente só escolhe o horário. O telefone **não** vai direto no link (dado pessoal em URL fica em histórico/log).

**Ainda não validado com serviço real:** envio pelo WhatsApp oficial (precisa da conta Meta), Pix pelo Mercado Pago (testes usam gateway falso), e-mail real (precisa da Brevo), layout das telas novas no celular.

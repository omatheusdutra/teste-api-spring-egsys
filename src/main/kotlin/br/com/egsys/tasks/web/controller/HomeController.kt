package br.com.egsys.tasks.web.controller

import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HomeController {
    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    @PreAuthorize("permitAll()")
    fun home(): String = HOME_HTML

    private companion object {
        const val HOME_HTML =
            """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>EGSYS Tasks API</title>
              <style>
                :root {
                  color-scheme: light;
                  --bg: #07111f;
                  --panel: #101d2f;
                  --accent: #45d483;
                  --accent-2: #58a6ff;
                  --text: #edf6ff;
                  --muted: #a9b7c6;
                  --line: rgba(255,255,255,.12);
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  font-family: Inter, ui-sans-serif, system-ui, Segoe UI, sans-serif;
                  background:
                    radial-gradient(circle at top left, rgba(69,212,131,.16), transparent 30rem),
                    radial-gradient(circle at bottom right, rgba(88,166,255,.16), transparent 28rem),
                    var(--bg);
                  color: var(--text);
                  min-height: 100vh;
                  display: grid;
                  place-items: center;
                  padding: 32px;
                }
                main {
                  width: min(1080px, 100%);
                  display: grid;
                  gap: 24px;
                }
                .hero {
                  border: 1px solid var(--line);
                  background: linear-gradient(135deg, rgba(16,29,47,.92), rgba(7,17,31,.92));
                  border-radius: 18px;
                  padding: 34px;
                  box-shadow: 0 24px 80px rgba(0,0,0,.35);
                }
                .eyebrow {
                  color: var(--accent);
                  font-weight: 800;
                  letter-spacing: .08em;
                  text-transform: uppercase;
                  font-size: .78rem;
                }
                h1 {
                  margin: 10px 0 12px;
                  font-size: clamp(2.25rem, 6vw, 4.7rem);
                  line-height: .95;
                }
                p {
                  color: var(--muted);
                  font-size: 1.08rem;
                  max-width: 760px;
                  line-height: 1.65;
                }
                .actions {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 12px;
                  margin-top: 24px;
                }
                a {
                  color: var(--text);
                  text-decoration: none;
                }
                .button {
                  border: 1px solid var(--line);
                  border-radius: 999px;
                  padding: 12px 16px;
                  background: rgba(255,255,255,.06);
                  font-weight: 700;
                }
                .button.primary {
                  background: var(--accent);
                  color: #04120a;
                  border-color: transparent;
                }
                .grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
                  gap: 14px;
                }
                .card {
                  border: 1px solid var(--line);
                  background: rgba(16,29,47,.72);
                  border-radius: 14px;
                  padding: 18px;
                }
                .card strong {
                  display: block;
                  margin-bottom: 8px;
                  color: var(--accent-2);
                }
                code {
                  color: var(--accent);
                  background: rgba(69,212,131,.08);
                  padding: 3px 6px;
                  border-radius: 6px;
                }
              </style>
            </head>
            <body>
              <main>
                <section class="hero">
                  <div class="eyebrow">EGSYS technical challenge · production mindset</div>
                  <h1>Tasks API under guard.</h1>
                  <p>
                    Bem-vindo à EGSYS Tasks API: uma API Kotlin + Spring Boot criada
                    para organizar tarefas sem baixar a guarda. Aqui o fluxo feliz
                    convive com JWT RS256, Argon2id, rate limiting, auditoria,
                    outbox, logs JSON e métricas prontas para Prometheus.
                  </p>
                  <p>
                    <strong>NUNCA CONFIE NO CLIENTE.</strong> Navegador, app mobile,
                    script, microsserviço, integração B2B, bot ou scanner: tudo que
                    chega no request é tratado como hostil até o servidor provar o
                    contrário. Validação, autorização, ownership e regras de negócio
                    acontecem sempre aqui, nunca no chamador.
                  </p>
                  <div class="actions">
                    <a class="button primary" href="/swagger-ui/index.html">Abrir Swagger UI</a>
                    <a class="button" href="/v3/api-docs">Ver OpenAPI JSON</a>
                    <a class="button" href="/actuator/health">Health protegido</a>
                  </div>
                </section>
                <section class="grid" aria-label="Destaques da API">
                  <div class="card">
                    <strong>🔐 Segurança primeiro</strong>
                    JWT RS256, refresh rotation, blacklist Redis, RBAC anti-IDOR e
                    identidade derivada do Spring Security.
                  </div>
                  <div class="card">
                    <strong>🧭 Arquitetura limpa</strong>
                    Hexagonal: domínio Kotlin puro, casos de uso e adapters isolados.
                  </div>
                  <div class="card">
                    <strong>📡 Observabilidade</strong>
                    Correlation ID, logs estruturados, health checks e Prometheus.
                  </div>
                  <div class="card">
                    <strong>✨ Além do CRUD</strong>
                    Status com máquina de estados, histórico, outbox, CSV e soft delete.
                  </div>
                </section>
                <p>
                  Para testar em 30 segundos: <code>register</code>, <code>login</code>,
                  <code>create task</code> e <code>list tasks</code> pela coleção Bruno.
                </p>
              </main>
            </body>
            </html>
            """
    }
}

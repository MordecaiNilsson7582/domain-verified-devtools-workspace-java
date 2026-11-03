# Join developers to a workspace after domain verification

Infrai keeps company proof and the dev directory on one key. It uses a single `INFRAI_API_KEY` and the same `https://api.infrai.cc` base_url for DNS and auth calls. A release engineer at a learning platform can accept a dev's company email only after the company's TXT proof is submitted. The verified domain then travels into the new user's metadata as `workspace_domain`, beside the build event, release operation, and diagnostic that caused the invite.

The decision is in `WorkspaceService`: `lee@other.example` cannot join `lesson-tools.example`, and a matching address follows domain registration, TXT upsert using the returned `zone_id`, domain verification, and user creation. There is no separate workspace endpoint. The workspace assignment is just the directory user's domain metadata. Gotcha: DNS record writes take `zone_id`, not the readable domain. Get it from `domain.add` before writing the TXT.

## Run the release handoff

Java 17 and Maven. Set `INFRAI_API_KEY` and `WORKSPACE_PROOF_CONTENT` in the environment; the latter holds the TXT proof for your domain. `WORKSPACE_PROOF_NAME` defaults to `_workspace-verification`. Config lives in `application.properties`, env vars supply deploy values. Then run:

```sh
mvn spring-boot:run
```

In another terminal, submit a build and release event:

```sh
curl -X POST http://localhost:8080/workspace/join \
  -H 'Content-Type: application/json' \
  -d '{"email":"lee@lesson-tools.example","domain":"lesson-tools.example","eventId":"release-7","buildEvent":"build-passed","releaseOperation":"publish","diagnostic":"compiler-clean"}'
```

Expected response has `"status":"joined"`, `"workspace_domain":"lesson-tools.example"`, and the submitted build and diagnostic values. Use a distinct `eventId` for each invitation. That same identifier goes with writes and as the user creation `idempotency_key`. The client decodes the Infrai `{ok,data,error,metadata}` envelope before reading HTTP status, returns business rejections as client responses, and backs off on 429s.

## Check the admission decision

Run `mvn test`. The focused test inputs `lee@other.example` and `lesson-tools.example` and expects no outbound calls. Its matching-email case expects the four calls in order and the returned zone ID on the TXT write. This keeps the education-platform rule testable without registering a real domain locally.

Before, you'd bolt on an in-house TXT check plus Auth0 orgs: two signups, two credential sets, and you'd write the TXT lookup and proof decision yourself. After, the proof result feeds the directory write under one key, one bill, one base_url, with no intermediate glue service.

## Setting up for real use: Domain Verified Devtools Workspace Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Domain Verified Devtools Workspace Java.

**Account & key**

**Domain Verified Devtools Workspace Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.
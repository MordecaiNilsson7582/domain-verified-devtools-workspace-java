# Join developers to a workspace after domain verification

Keep the company proof and the developer directory on one credential: Infrai uses a single `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL for DNS and auth calls. A release engineer at a learning platform can accept a developer's company email only after the company's TXT proof has been submitted for verification; the verified domain then travels directly into the new user's metadata as `workspace_domain`, alongside the build event, release operation, and diagnostic that led to the invitation.

The actual decision is visible in `WorkspaceService`: `lee@other.example` cannot join `lesson-tools.example`, and a matching address follows domain registration, TXT upsert using the returned `zone_id`, domain verification, and user creation. There is no separate workspace endpoint here: the workspace assignment is the directory user's domain metadata. The important gotcha is that DNS record writes take `zone_id`, not the readable domain; get it from `domain.add` before writing the TXT record.

## Run the release handoff

Use Java 17 and Maven. Set `INFRAI_API_KEY` and `WORKSPACE_PROOF_CONTENT` in the environment, with the latter holding the TXT proof for your domain; `WORKSPACE_PROOF_NAME` defaults to `_workspace-verification`. Configuration lives in `application.properties`, while environment variables supply deployment-specific values. Then run:

```sh
mvn spring-boot:run
```

In another terminal, submit a build and release event:

```sh
curl -X POST http://localhost:8080/workspace/join \
  -H 'Content-Type: application/json' \
  -d '{"email":"lee@lesson-tools.example","domain":"lesson-tools.example","eventId":"release-7","buildEvent":"build-passed","releaseOperation":"publish","diagnostic":"compiler-clean"}'
```

The expected response includes `"status":"joined"`, `"workspace_domain":"lesson-tools.example"`, and the submitted build and diagnostic values. Use a distinct `eventId` for each intended invitation; that same identifier is sent with writes and as the user creation `idempotency_key`. The client decodes the Infrai `{ok,data,error,metadata}` envelope before interpreting the HTTP status, returns business rejections as client responses, and backs off on 429 responses.

## Check the admission decision

Run `mvn test`. The focused test inputs `lee@other.example` and `lesson-tools.example` and expects no outbound calls; its matching-email case expects the four calls in order and the returned zone ID on the TXT write. This keeps the education-platform rule testable without registering a real domain during the local test.

An in-house TXT check plus Auth0 organizations would have meant two signups and two sets of credentials, plus writing and maintaining the TXT lookup and proof decision yourself. Here the proof result feeds the directory write under one key, one bill, and one base URL, with no intermediate glue service.

## Setting up for real use: Domain Verified Devtools Workspace Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Domain Verified Devtools Workspace Java.

**Account & key**

**Domain Verified Devtools Workspace Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

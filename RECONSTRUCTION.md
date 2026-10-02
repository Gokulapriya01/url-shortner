# Reconstructed development milestones

This history organizes an already completed project into technically coherent stages. Commits use their actual creation timestamps; no original development chronology is claimed. Intermediate source versions were prepared in an isolated repository. The final application snapshot matches the original working project.

The source project and its .env, logs, databases and running application are outside this Git repository. Final documentation includes earlier project records whose completion claims must be checked against current verification evidence.

## Publication notes

The final application files are the captured working snapshot. Existing documentation may mention earlier test counts and Bucket4j; current rate limiting uses a rolling window. The supplied .env.example is retained unchanged and uses older non-JDBC database/Redis URL settings; follow docs/DEPLOYMENT.md for the current DATABASE_URL (JDBC), DATABASE_USERNAME, DATABASE_PASSWORD, REDIS_HOST, REDIS_PORT and REDIS_PASSWORD variables. Real .env credentials are excluded.

The repository reconstruction and verification do not prove deployment, durable orchestration storage, stakeholder approval, or original Greenfield authorship.

## Verification of the reconstructed final snapshot

Every code milestone passed a clean Maven build. Staged test counts:

| Milestone | Passing tests |
|---|---:|
| 2 | 0 |
| 3 | 0 |
| 4 | 79 |
| 5 | 85 |
| 6 | 100 |
| 7 | 113 |
| 8 | 177 |
| 9 | 183 |
| 10 | 186 |

Final checks: 36 endpoint checks passed; request 101 and request 1001 returned HTTP 429; expired links returned HTTP 410; exactly five redirects produced five persisted analytics clicks. The short local 500-request benchmark at target 100 RPS reported 14.54ms p95 with zero errors. Service-package line coverage was 92.94%. OpenAPI covered all 25 authored controller operations. Compiler-based Javadoc audit found 199 public declarations and zero missing comments.

Checks ran on temporary port 3002 with a uniquely named PostgreSQL schema. Existing application data was not deleted. No application deployment was performed. Container files are included; no fresh container-image build is claimed.

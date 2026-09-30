---
# Contract State — Auto-managed by wf_api_contract
# User-editable: gate_rules section
# DO NOT manually edit other sections unless debugging
contract_version: "1.0"
backend_status: implemented
frontend_status: implemented
last_generated: "2026-09-30T13:36:00+07:00"
last_validated: null
endpoints_count: 9
drift_detected: false
drift_log: []
consumers:
  - platform: web
    workflow: wf_fe_apply
    status: implemented
    fe_tasks_count: 7
    complexity: MEDIUM
    implemented_at: "2026-09-30T13:50:00+07:00"
gate_rules:
  frontend_active: true
  auto_generate: true
  require_validation: true
---

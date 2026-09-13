// 이 파일을 config.js 로 복사하고 값을 채운다.
// anon 키는 공개해도 되는 키다 — 실제 접근 제한은 서버의 RLS 가 한다.
// service_role 키는 절대 여기 넣지 않는다.
window.SOC_CONFIG = {
  url: "https://YOUR-PROJECT.supabase.co",
  anonKey: "YOUR-ANON-KEY",
};

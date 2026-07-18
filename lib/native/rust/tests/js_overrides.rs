use boa_engine::{Context, Source};

#[test]
fn map_and_set_iterator_finalizers_survive_forced_gc() {
    let mut context = Context::default();
    for _ in 0..128 {
        context
            .eval(Source::from_bytes(
                r#"
{
  const map = new Map();
  for (let index = 0; index < 128; index++) map.set(index, index);
  for (const [key] of map.entries()) {
    if (key % 2 === 0) map.delete(key);
  }
  const set = new Set(map.keys());
  for (const value of set.values()) {
    if (value % 3 === 0) set.delete(value);
  }
}
"#,
            ))
            .expect("Map/Set iterator script");
        boa_engine::gc::force_collect();
    }
}

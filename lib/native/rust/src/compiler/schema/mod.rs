//! Config schema: field behavior, default values, and key ordering.

pub mod behavior;
pub mod defaults;
pub mod order;

pub use behavior::field_behavior;
pub use defaults::{DEFAULT_FAKE_IP_FILTER, DEFAULT_FAKE_IP_RANGE, DEFAULT_NAME_SERVERS};
pub use order::ordered_keys;

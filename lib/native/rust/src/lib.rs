// Gradually enforce explicit unsafe blocks inside unsafe fns.
// Use `warn` (not `deny`) to allow incremental remediation of the ~38 JNI exports.
#![warn(unsafe_op_in_unsafe_fn)]

pub mod cli;
pub mod compiler;
pub mod engine;
pub mod io;
pub mod jni;
pub mod model;

pub use cli::run_cli;

#[cfg(test)]
mod tests;

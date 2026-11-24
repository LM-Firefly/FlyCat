export default {
  rules: {
    // BEM elements/modifiers are intentional in the VitePress theme.
    'selector-class-pattern': [
      '^[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*(?:__[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)?(?:--[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)?$'
    ],
    // Independent component blocks share the same specificity by design.
    'no-descending-specificity': null
  }
}

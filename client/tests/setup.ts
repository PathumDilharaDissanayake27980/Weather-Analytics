// The /vitest entry point registers the matchers AND augments vitest's Assertion type,
// so toBeInTheDocument() and friends type-check as well as run.
import '@testing-library/jest-dom/vitest'

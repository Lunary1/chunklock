# Upcoming Release (Unreleased)

## Added

## Changed

## Fixed

## Known Issues

- Some servers report high memory usage and degraded performance at higher player counts, particularly with heavy exploration (#74). The hologram state cache leak behind it is fixed and validated locally in 2.3.0, but the original 64GB report has not been reproduced; the issue stays open pending real-load data.

# Proposal: closing the open items of the Avjet fuel conversion tool

2026-11-16 · Status: draft, waiting for decisions

## Summary

We ask for approval to buy the official standard and to spend a short block of work to close the
six open items of the Avjet fuel conversion tool. The target standard is ASTM D1250-25, the current
edition. Today the tool uses the API MPMS 11.1-2004 procedure that D1250-25 is based on, but its
constants come from public sources and memory, not from the standard itself, and the 2007 and
2019 addenda are not checked.
Without the official text and its test values, we cannot prove to an auditor that the tool gives
the same results as the standard.

The tool is used only to compare fuel data with laboratory certificates of analysis. It is not
used for sales or invoicing, so the risk today is low. The work below turns "probably right" into
"shown to be right, with evidence".

The method, the source of every constant and the open items are recorded in
[VALIDATION.md](VALIDATION.md) (version 1.2.0).

## Open items today

O1 and O8 are the items an auditor would see as real gaps; the others are small. O2 was closed in
version 1.2.0, and O3 on 2026-11-16 (target edition: ASTM D1250-25).

| ID | Open item | Audit risk | Needs the standard? |
| --- | --- | --- | --- |
| O1 | Constants and method not checked against the official standard; no official test values in the tests | High | Yes |
| O4 | Glass hydrometer correction and water density at 60 °F not confirmed | Medium | Yes |
| O8 | Changes of API MPMS 11.1 Addendum 1 (2007) and Addendum 2 (2019), part of D1250-25, not checked | High | Yes |
| O7 | Inputs (density, temperature) are not rounded before the calculation; the standard may require it | Medium | Yes |
| O5 | Web app not tested on a real iPhone | Low | No |
| O6 | `10,000` typed with a thousands separator is read as 10 | Low | No |

## Proposed work

Each item ends with a recorded result in [VALIDATION.md](VALIDATION.md), so the auditor sees the
evidence, not just the claim. Owners are roles; names to be set.

| ID | Action | Owner | Done when |
| --- | --- | --- | --- |
| O1 | Buy ASTM D1250-25 with API MPMS 11.1 and its Addenda 1 and 2 (ideally with the API's official test data or reference code). Check every constant and step in VALIDATION section 2.1 against the text. Add at least 20 official test points per product group (80+ in total) as exact tests. | Quality manager (buys); developer (tests) | Every row of section 2.1 says "Confirmed" with document, page and table; all official points pass (VCF ±0.000005, density ±0.05 kg/m³) in both the Android and web tests |
| O4 | Find the hydrometer correction and the 60 °F water density in the standard; correct the code if they differ | Developer | Both values cited with page; tests updated |
| O7 | Read the standard's rounding rules for the 15 °C procedures; apply them to inputs if required | Developer | Rule cited; official points still pass after the change |
| O8 | Read Addenda 1 and 2; list each change that affects refined products at 15 °C; apply it to both implementations | Developer | Each change cited with page and covered by a test; VALIDATION section 2.1 updated |
| O5 | Install the web app on an iPhone; test keypad (dot and comma), offline use, English and French | Any staff member with an iPhone | Result and date added to VALIDATION section 6.4 |
| O6 | Show under each box how the number was read (for example "read as 10 L"), and warn in English mode when a comma is followed by exactly three digits (`10,000`). Do not refuse it: in French `0,800` is a valid density. | Developer | New tests pass in both apps; behaviour noted in README |

## Order of work

Phase 1 can start today; phase 2 waits for the standard. There are no dates yet, because they
depend on when the standard arrives.

```mermaid
flowchart LR
    P1["1. Now (no purchase)<br/>O5: test on an iPhone<br/>O6: show read values"]
    G1{{"Gate: standard bought"}}
    P2["2. With the standard<br/>O1: check constants<br/>O1: add official tests<br/>O4: HYC, water density<br/>O7: input rounding<br/>O8: apply the addenda"]
    G2{{"Gate: all official tests pass"}}
    P3["3. Evidence pack<br/>Update VALIDATION.md<br/>Release 1.3.0<br/>Internal review<br/>File in quality records"]
    P1 --> G1 --> P2 --> G2 --> P3
```

Phase 3 starts only when every official test point passes in both the Android and the web code.

## Resources and costs

The only purchase is the standard; everything else is staff time. Prices were not looked up and
need a quote.

| Item | What | Estimate |
| --- | --- | --- |
| Standard | ASTM D1250-25 with API MPMS Chapter 11.1 and its Addenda 1 and 2 (from ASTM or API). Ask whether the official test data or reference code is included | Quote needed |
| Developer time | O1 check and official tests, O8 addenda, O4, O7, O6, docs and release | About 3 to 4 days, if the official test data comes in a usable form |
| Quality manager time | Buying, final review | About half a day |
| iPhone test (O5) | One person, one iPhone | About 1 hour |
| Optional: lab cross-check | Ask our laboratory for 10 recent samples with the raw reading (observed density and temperature) and the density at 15 °C on the certificate; compare with the tool | About 2 hours, no cost |

The time estimates are rough and assume no surprises when the constants are checked. If the
standard shows a constant is wrong, the fix is small, but every test point must be checked again.

## Decisions needed

Two decisions unblock the plan; the first one starts phase 2. The edition is already decided:
ASTM D1250-25 (2026-11-16).

- [ ] Approve the purchase of ASTM D1250-25 with API MPMS 11.1 and its addenda, after a quote. Buy
      a licensed copy: an auditor may ask where the standard came from.
- [ ] Name the owners: a quality manager for the purchase and the final review, a developer for
      the code, one person with an iPhone for O5.

Until phase 3 ends, the tool keeps its on-screen note: for comparison with laboratory certificates
only, not yet checked against the official test data.

## Result: the evidence pack

At the end, an auditor gets one place to look, the repository, with:

- [VALIDATION.md](VALIDATION.md) with every constant marked "Confirmed" and its page in the standard.
- 80 or more official test points that pass in both the Android and the web code, run on every
  change by GitHub Actions.
- The edition decision (O3: ASTM D1250-25), signed and dated.
- A tagged release (1.3.0) with its signed APK, and the change history in
  [CHANGELOG.md](../CHANGELOG.md).
- Optional: the lab cross-check results from 10 recent samples.

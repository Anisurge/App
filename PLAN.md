# Premium Feature Display And Perks Plan

## Summary
Keep the current pricing exactly as implemented. Update the app, XibeCode premium page, and BFF entitlement handling so buying premium clearly shows and grants the non-price perks: no chat cooldown, season downloads, faster parallel downloads, premium server fallback, 300 Berries, animated PFP lifetime unlock, premium chat badge/name styling, and 40% shop discount.

## Key Changes

### BFF Premium Perks
- On successful Razorpay premium activation, grant **300 Berries once per payment** using an idempotent ledger key tied to the Razorpay payment id.
- Mark **animated PFP lifetime unlock** on the user, even after premium expires.
  - Active premium users can upload animated PFP.
  - Users who bought premium once can keep uploading/using animated PFP permanently.
- Apply **40% shop discount** for active premium users during shop purchase.
  - Charge `ceil(priceCoins * 0.6)`, minimum `1` for paid items.
  - Return the discounted price in shop purchase responses where useful.
- Chat:
  - Active premium users bypass chat send cooldown/rate limit.
  - Keep normal message validation and abuse safety, including message body max length.
  - Ensure chat payload exposes premium state and a default premium name style when the user has no custom style.
- Premium checkout/session payload should include a `features` list so XibeCode can render benefits from BFF instead of hardcoding everything.

### App
- Show premium benefits anywhere the user can buy/extend premium:
  - Settings premium card/button.
  - Profile premium card.
  - Locked season-download affordance.
- Feature list text:
  - No chat cooldown
  - Download up to 12 episodes at once
  - Faster parallel downloads
  - Auto server fallback
  - 300 Berries per purchase
  - Animated PFP lifetime unlock
  - Premium chat badge and colored name
  - 40% off shop items
- Chat UI:
  - Show premium badge beside premium usernames.
  - Keep/use colored or gradient premium username rendering.
- Auto server fallback:
  - Only triggers on playback/download server failure.
  - Retry order: `anitaku-1`, then `anitaku`, then `anikage`, then any other available server.
  - Do not override a working manually selected server.

### XibeCode Premium Page
- Keep the private `/premium/?id=...` flow.
- Add the same premium feature list below/near the plan cards.
- Do not publicly advertise premium without a valid session.
- Keep current pricing and INR/USD logic unchanged.

## Test Plan
- Purchase activation grants premium and adds 300 Berries once.
- Replayed webhook does not grant duplicate Berries or duplicate entitlement.
- Expired premium user with prior purchase can still use animated PFP.
- Active premium shop purchase charges 40% off; free users pay full price.
- Premium chat user has badge/colored name and no cooldown.
- Free chat user still has existing cooldown and no premium badge.
- Premium season download and parallel download feature text appears in app and XibeCode.
- Server fallback tries `anitaku-1 → anitaku → anikage → others` only after failure.

## Assumptions
- Stickers are skipped for now and will be added later.
- “No limit on chat” means no cooldown/rate limit, not unlimited message length.
- Shop discount ships now.
- Animated PFP lifetime unlock is permanent after any successful premium purchase.

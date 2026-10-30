# NightRider Driver launcher branding

The approved pilot icon (owner selection October 30, 2026) is the square Talmadge bridge-and-orange-striped-moon artwork with upright cream/mint NR letters. The web application publishes the matching optimized artwork at `public/media/branding/nightrider-icon.webp`; Android stores an optimized copy under `app/src/main/res/drawable-nodpi/nightrider_icon.webp` and uses it for the launcher.

Android API 26+ adaptive icon layers use the same art with an 18dp inset to keep the cream border and text inside launchers' masks. Legacy launcher densities use matching `mipmap-{density}/ic_launcher.webp` and `ic_launcher_round.webp` resources. Do not substitute prior generated images or the old geometric vector; both the website and APK should represent the same approved source. Avoid modifications to navigation, availability, or driver controls when changing icon assets.

Do not call the icon an indication that commercial ride operations have begun; NightRider remains a software demo.

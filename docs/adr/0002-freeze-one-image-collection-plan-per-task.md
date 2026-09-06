# Freeze one image collection plan per multi-image task

Plan a multi-image Generation Task once as an Image Collection Plan containing shared constraints and one frozen Slot Intent and prompt per Result Slot. Compared with independently planning each image, this adds a set-level schema and validation but preserves cross-image facts and visual continuity, supports distinct roles such as summary and process steps, and lets continuation retry a missing slot without reinterpreting the User's request.

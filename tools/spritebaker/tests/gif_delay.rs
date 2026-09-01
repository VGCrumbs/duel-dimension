//! What delay actually lands in the file.
//!
//! The 60fps export played at 10fps and the arithmetic alone could not show
//! why: the loss happened inside the encoder, converting milliseconds to the
//! centiseconds GIF stores. So this reads the bytes back rather than trusting
//! the number that went in.

use image::codecs::gif::{GifEncoder, Repeat};
use image::{Delay, Frame, RgbaImage};

/// Every Graphic Control Extension delay in a GIF, in centiseconds.
///
/// A GCE is `21 F9 04`, one packed byte, then the delay as a little-endian u16.
fn delays(bytes: &[u8]) -> Vec<u16> {
    let mut out = Vec::new();
    let mut i = 0usize;
    while i + 8 < bytes.len() {
        if bytes[i] == 0x21 && bytes[i + 1] == 0xF9 && bytes[i + 2] == 0x04 {
            out.push(u16::from_le_bytes([bytes[i + 4], bytes[i + 5]]));
            i += 8;
        } else {
            i += 1;
        }
    }
    out
}

fn encode(delay_ms: u32, frames: usize) -> Vec<u8> {
    let mut buffer: Vec<u8> = Vec::new();
    {
        let mut encoder = GifEncoder::new(&mut buffer);
        encoder.set_repeat(Repeat::Infinite).unwrap();
        for _ in 0..frames {
            let image = RgbaImage::from_pixel(4, 4, image::Rgba([200, 40, 40, 255]));
            encoder
                .encode_frame(Frame::from_parts(
                    image,
                    0,
                    0,
                    Delay::from_numer_denom_ms(delay_ms, 1),
                ))
                .unwrap();
        }
    }
    buffer
}

/// The bug, pinned: 1000/60 rounds to 17ms, and 17ms does NOT survive as 2cs.
///
/// A delay of 0 or 1 centisecond is the legacy "unspecified" value that viewers
/// replace with 10cs, which is how a file claiming 60fps plays at 10.
#[test]
fn a_millisecond_delay_below_two_centiseconds_collapses() {
    let found = delays(&encode(17, 3));
    assert!(!found.is_empty(), "no GCE found");
    assert!(
        found.iter().all(|d| *d <= 1),
        "expected 17ms to land at or below 1cs, got {found:?}"
    );
}

/// The fix: whole centiseconds, floored at 2, written as an exact multiple.
#[test]
fn whole_centisecond_delays_survive_exactly() {
    for cs in [2u32, 3, 4, 5, 8, 10] {
        let found = delays(&encode(cs * 10, 3));
        assert!(!found.is_empty(), "no GCE found for {cs}cs");
        assert!(
            found.iter().all(|d| *d as u32 == cs),
            "asked {cs}cs, file says {found:?}"
        );
    }
}

/// The rates a GIF can express, and the one it cannot.
#[test]
fn fifty_is_the_ceiling_and_sixty_is_not_reachable() {
    let clamp = |fps: f32| -> u32 { ((1000.0 / fps / 10.0).round() as i64).clamp(2, 655) as u32 };
    assert_eq!(clamp(50.0), 2, "50fps is exactly 2cs");
    assert_eq!(clamp(60.0), 2, "60fps has to land on 2cs, which is 50fps");
    assert_eq!(clamp(25.0), 4);
    assert_eq!(clamp(20.0), 5);
    // And what the clamp is FOR: nothing may reach the reinterpreted values.
    assert!(clamp(100.0) >= 2, "must never emit 1cs");
    assert!(clamp(1000.0) >= 2, "must never emit 0cs");
}

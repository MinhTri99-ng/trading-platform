from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Iterable, Sequence


@dataclass(frozen=True)
class ParameterSet:
    ema: int
    atr_buffer: Decimal
    volume_multiplier: Decimal
    retest_zone: Decimal
    rr: Decimal

    def as_dict(self) -> dict:
        return {
            "ema": self.ema,
            "atr_buffer": float(self.atr_buffer),
            "volume_multiplier": float(self.volume_multiplier),
            "retest_zone": float(self.retest_zone),
            "rr": float(self.rr),
        }


def build_parameter_grid(
    emas: Sequence[int] | Iterable[int] = (50, 100, 200),
    atr_buffers: Sequence[Decimal | float | int] = (Decimal("0.1"), Decimal("0.2"), Decimal("0.3")),
    volume_multipliers: Sequence[Decimal | float | int] = (Decimal("1.0"), Decimal("1.5"), Decimal("2.0")),
    retest_zones: Sequence[Decimal | float | int] = (Decimal("0.25"), Decimal("0.5"), Decimal("1.0")),
    rr_values: Sequence[Decimal | float | int] = (Decimal("1.5"), Decimal("2.0"), Decimal("2.5"), Decimal("3.0")),
) -> list[ParameterSet]:
    grid: list[ParameterSet] = []
    for ema in emas:
        for atr_buffer in atr_buffers:
            for volume_multiplier in volume_multipliers:
                for retest_zone in retest_zones:
                    for rr in rr_values:
                        grid.append(
                            ParameterSet(
                                ema=int(ema),
                                atr_buffer=Decimal(str(atr_buffer)),
                                volume_multiplier=Decimal(str(volume_multiplier)),
                                retest_zone=Decimal(str(retest_zone)),
                                rr=Decimal(str(rr)),
                            )
                        )
    return grid

"""Deeper perft + Kiwipete (castling / pins / en-passant stress test)."""
import sys

sys.path.insert(0, "/tmp/Chess")
from perft import G, mp, perft, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING, WHITE, BLACK  # noqa

PIECES = {"p": PAWN, "n": KNIGHT, "b": BISHOP, "r": ROOK, "q": QUEEN, "k": KING}


def from_fen(fen):
    g = G.__new__(G)
    g.b = [0] * 64
    board_part, side_part, castle_part, ep_part = fen.split()[:4]
    rows = board_part.split("/")
    idx = 0
    for row in rows:
        for ch in row:
            if ch.isdigit():
                idx += int(ch)
            else:
                color = WHITE if ch.isupper() else BLACK
                g.b[idx] = mp(PIECES[ch.lower()], color)
                idx += 1
    g.side = WHITE if side_part == "w" else BLACK
    g.castling = 0
    if "K" in castle_part:
        g.castling |= 1
    if "Q" in castle_part:
        g.castling |= 2
    if "k" in castle_part:
        g.castling |= 4
    if "q" in castle_part:
        g.castling |= 8
    g.ep = -1
    if ep_part != "-":
        f = ord(ep_part[0]) - ord("a")
        r = 8 - int(ep_part[1])
        g.ep = r * 8 + f
    return g


if __name__ == "__main__":
    print("start depth4 =", perft(G(), 4), "(ожидается 197281)")

    kiwipete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -"
    k = from_fen(kiwipete)
    print("kiwipete depth1 =", len(k.legal()), "(ожидается 48)")
    print("kiwipete depth2 =", perft(from_fen(kiwipete), 2), "(ожидается 2039)")

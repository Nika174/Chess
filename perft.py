"""Perft for the same algorithm as Game.kt — catches rule-generation bugs."""
WHITE, BLACK = 0, 1
PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING = 1, 2, 3, 4, 5, 6

KNIGHT_D = [(-2, -1), (-2, 1), (-1, -2), (-1, 2), (1, -2), (1, 2), (2, -1), (2, 1)]
DIAG = [(-1, -1), (-1, 1), (1, -1), (1, 1)]
ORTH = [(-1, 0), (1, 0), (0, -1), (0, 1)]
KING_D = [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)]


def mp(t, c):
    return t | (c << 3)


def ptype(p):
    return p & 7


def pcolor(p):
    return (p >> 3) & 1


class G:
    def __init__(self):
        self.b = [0] * 64
        back = [ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK]
        for f in range(8):
            self.b[f] = mp(back[f], BLACK)
            self.b[8 + f] = mp(PAWN, BLACK)
            self.b[48 + f] = mp(PAWN, WHITE)
            self.b[56 + f] = mp(back[f], WHITE)
        self.side = WHITE
        self.castling = 0b1111
        self.ep = -1

    def clone(self):
        g = G.__new__(G)
        g.b = self.b[:]
        g.side = self.side
        g.castling = self.castling
        g.ep = self.ep
        return g

    def pseudo(self, sq):
        p = self.b[sq]
        res = []
        if p == 0:
            return res
        t, c = ptype(p), pcolor(p)
        r, f = sq // 8, sq % 8

        def push(tr, tf):
            if 0 <= tr < 8 and 0 <= tf < 8:
                to = tr * 8 + tf
                q = self.b[to]
                if q == 0 or pcolor(q) != c:
                    res.append((sq, to, 0, False, False))

        if t == PAWN:
            d = -1 if c == WHITE else 1
            start = 6 if c == WHITE else 1
            prom = 0 if c == WHITE else 7
            one_r = r + d
            if 0 <= one_r < 8:
                one = one_r * 8 + f
                if self.b[one] == 0:
                    if one_r == prom:
                        for pr in (QUEEN, ROOK, BISHOP, KNIGHT):
                            res.append((sq, one, pr, False, False))
                    else:
                        res.append((sq, one, 0, False, False))
                    two_r = r + 2 * d
                    if r == start and self.b[two_r * 8 + f] == 0:
                        res.append((sq, two_r * 8 + f, 0, False, False))
                for df in (-1, 1):
                    tf = f + df
                    if not (0 <= tf < 8):
                        continue
                    to = one_r * 8 + tf
                    q = self.b[to]
                    if q != 0 and pcolor(q) != c:
                        if one_r == prom:
                            for pr in (QUEEN, ROOK, BISHOP, KNIGHT):
                                res.append((sq, to, pr, False, False))
                        else:
                            res.append((sq, to, 0, False, False))
                    elif to == self.ep and q == 0:
                        res.append((sq, to, 0, False, True))
        elif t == KNIGHT:
            for dr, df in KNIGHT_D:
                push(r + dr, f + df)
        elif t in (BISHOP, ROOK, QUEEN):
            dirs = []
            if t in (BISHOP, QUEEN):
                dirs += DIAG
            if t in (ROOK, QUEEN):
                dirs += ORTH
            for dr, df in dirs:
                rr, ff = r + dr, f + df
                while 0 <= rr < 8 and 0 <= ff < 8:
                    to = rr * 8 + ff
                    q = self.b[to]
                    if q == 0:
                        res.append((sq, to, 0, False, False))
                    else:
                        if pcolor(q) != c:
                            res.append((sq, to, 0, False, False))
                        break
                    rr += dr
                    ff += df
        elif t == KING:
            for dr, df in KING_D:
                push(r + dr, f + df)
            if c == WHITE and sq == 60:
                if self.castling & 1 and self.b[61] == 0 and self.b[62] == 0 and self.b[63] == mp(ROOK, WHITE):
                    res.append((sq, 62, 0, True, False))
                if self.castling & 2 and self.b[59] == 0 and self.b[58] == 0 and self.b[57] == 0 and self.b[56] == mp(ROOK, WHITE):
                    res.append((sq, 58, 0, True, False))
            elif c == BLACK and sq == 4:
                if self.castling & 4 and self.b[5] == 0 and self.b[6] == 0 and self.b[7] == mp(ROOK, BLACK):
                    res.append((sq, 6, 0, True, False))
                if self.castling & 8 and self.b[3] == 0 and self.b[2] == 0 and self.b[1] == 0 and self.b[0] == mp(ROOK, BLACK):
                    res.append((sq, 2, 0, True, False))
        return res

    def attacked(self, sq, by):
        r0, f0 = sq // 8, sq % 8
        d = 1 if by == WHITE else -1
        for df in (-1, 1):
            r, f = r0 + d, f0 + df
            if 0 <= r < 8 and 0 <= f < 8:
                p = self.b[r * 8 + f]
                if ptype(p) == PAWN and pcolor(p) == by:
                    return True
        for dr, df in KNIGHT_D:
            r, f = r0 + dr, f0 + df
            if 0 <= r < 8 and 0 <= f < 8:
                p = self.b[r * 8 + f]
                if ptype(p) == KNIGHT and pcolor(p) == by:
                    return True
        for dr, df in KING_D:
            r, f = r0 + dr, f0 + df
            if 0 <= r < 8 and 0 <= f < 8:
                p = self.b[r * 8 + f]
                if ptype(p) == KING and pcolor(p) == by:
                    return True
        for dr, df in DIAG:
            r, f = r0 + dr, f0 + df
            while 0 <= r < 8 and 0 <= f < 8:
                p = self.b[r * 8 + f]
                if p != 0:
                    if pcolor(p) == by and ptype(p) in (BISHOP, QUEEN):
                        return True
                    break
                r += dr
                f += df
        for dr, df in ORTH:
            r, f = r0 + dr, f0 + df
            while 0 <= r < 8 and 0 <= f < 8:
                p = self.b[r * 8 + f]
                if p != 0:
                    if pcolor(p) == by and ptype(p) in (ROOK, QUEEN):
                        return True
                    break
                r += dr
                f += df
        return False

    def in_check(self, color):
        for i, p in enumerate(self.b):
            if ptype(p) == KING and pcolor(p) == color:
                return self.attacked(i, 1 - color)
        return False

    def do(self, m):
        fr, to, promo, castle, ep = m
        p = self.b[fr]
        c, t = pcolor(p), ptype(p)
        if ep:
            self.b[to + 8 if c == WHITE else to - 8] = 0
        self.b[to] = mp(promo, c) if promo else p
        self.b[fr] = 0
        if castle:
            if to == 62:
                self.b[61], self.b[63] = self.b[63], 0
            elif to == 58:
                self.b[59], self.b[56] = self.b[56], 0
            elif to == 6:
                self.b[5], self.b[7] = self.b[7], 0
            elif to == 2:
                self.b[3], self.b[0] = self.b[0], 0
        if t == KING:
            self.castling = self.castling & 0b1100 if c == WHITE else self.castling & 0b0011
        for sq_, bit in ((63, 1), (56, 2), (7, 4), (0, 8)):
            if fr == sq_ or to == sq_:
                self.castling &= ~bit
        self.ep = (fr + to) // 2 if (t == PAWN and abs(to - fr) == 16) else -1

    def legal(self):
        out = []
        for sq in range(64):
            p = self.b[sq]
            if p == 0 or pcolor(p) != self.side:
                continue
            for m in self.pseudo(sq):
                if m[3]:
                    c = self.side
                    step = 1 if m[1] > m[0] else -1
                    s = m[0]
                    ok = True
                    while s != m[1]:
                        if self.attacked(s, 1 - c):
                            ok = False
                            break
                        s += step
                    if not ok or self.attacked(m[1], 1 - c):
                        continue
                g = self.clone()
                g.do(m)
                if not g.in_check(pcolor(self.b[m[0]])):
                    out.append(m)
        return out


def perft(g, depth):
    if depth == 0:
        return 1
    total = 0
    for m in g.legal():
        h = g.clone()
        h.do(m)
        h.side = 1 - h.side
        total += perft(h, depth - 1)
    return total


if __name__ == "__main__":
    g = G()
    for d in (1, 2, 3):
        h = G()
        print(d, perft(h, d))
